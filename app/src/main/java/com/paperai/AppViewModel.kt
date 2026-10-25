// ==================== app/src/main/java/com/paperai/AppViewModel.kt ====================
package com.paperai

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    var data: AppData by mutableStateOf(AppData())
        private set

    var busyNodes: Set<String> by mutableStateOf(emptySet())
        private set

    init {
        data = store.load()
    }

    private fun commit(d: AppData) {
        data = d
        viewModelScope.launch(Dispatchers.IO) { store.save(d) }
    }

    fun imageDir() = store.imageDir()

    // ---------------- 书库 ----------------

    fun itemsIn(parent: String?): List<LibItem> =
        data.items.filter { it.parent == parent }.sortedBy { it.order }

    fun item(id: String?): LibItem? = data.items.firstOrNull { it.id == id }

    fun pathOf(folderId: String?): List<LibItem> {
        val out = ArrayList<LibItem>()
        var cur = item(folderId)
        var guard = 0
        while (cur != null && guard++ < 100) {
            out.add(cur)
            cur = item(cur.parent)
        }
        out.reverse()
        return out
    }

    fun createFolder(parent: String?): String {
        val id = uid()
        val order = itemsIn(parent).size
        val it0 = LibItem(
            id = id, type = "folder", name = "新建文件夹",
            parent = parent, order = order, color = 0,
            created = nowMs(), updated = nowMs()
        )
        commit(data.copy(items = data.items + it0))
        return id
    }

    fun createBook(parent: String?): String {
        val id = uid()
        val order = itemsIn(parent).size
        val color = (data.items.count { it.type == "book" }) % 6
        val book = LibItem(
            id = id, type = "book", name = "",
            parent = parent, order = order, color = color,
            created = nowMs(), updated = nowMs()
        )
        commit(
            data.copy(
                items = data.items + book,
                convs = data.convs + (id to Conversation(id))
            )
        )
        return id
    }

    fun renameItem(id: String, name: String) {
        commit(data.copy(items = data.items.map { if (it.id == id) it.copy(name = name, updated = nowMs()) else it }))
    }

    fun deleteItem(id: String) {
        val toRemove = mutableSetOf<String>()
        fun collect(x: String) {
            if (!toRemove.add(x)) return
            data.items.filter { it.parent == x }.forEach { collect(it.id) }
        }
        collect(id)
        commit(
            data.copy(
                items = data.items.filterNot { it.id in toRemove },
                convs = data.convs.filterKeys { it !in toRemove }
            )
        )
    }

    fun moveToFolder(id: String, target: String?) {
        if (id == target) return
        // 防止把文件夹移进自己的子孙
        var p = target
        var guard = 0
        while (p != null && guard++ < 100) {
            if (p == id) return
            p = item(p)?.parent
        }
        val order = itemsIn(target).size
        commit(data.copy(items = data.items.map {
            if (it.id == id) it.copy(parent = target, order = order) else it
        }))
    }

    fun moveItem(parent: String?, from: Int, to: Int) {
        val list = itemsIn(parent).toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val moved = list.removeAt(from)
        list.add(to, moved)
        val orderMap = list.mapIndexed { i, it -> it.id to i }.toMap()
        commit(data.copy(items = data.items.map {
            val o = orderMap[it.id]
            if (o != null) it.copy(order = o) else it
        }))
    }

    fun allFolders(): List<LibItem> = data.items.filter { it.type == "folder" }

    // ---------------- 对话 ----------------

    fun conversation(id: String): Conversation = data.convs[id] ?: Conversation(id)

    private fun setConversation(c: Conversation) {
        commit(data.copy(
            convs = data.convs + (c.id to c),
            items = data.items.map {
                if (it.id == c.id) it.copy(updated = nowMs()) else it
            }
        ))
    }

    private fun appendNodes(bookId: String, nodes: List<MessageNode>) {
        val c = conversation(bookId)
        setConversation(c.copy(nodes = c.nodes + nodes))
    }

    fun updateNode(bookId: String, nodeId: String, transform: (MessageNode) -> MessageNode) {
        val c = conversation(bookId)
        setConversation(c.copy(nodes = c.nodes.map { if (it.id == nodeId) transform(it) else it }))
    }

    fun deleteNode(bookId: String, nodeId: String) {
        val c = conversation(bookId)
        // 删除该节点及其所有子孙
        val toRemove = mutableSetOf(nodeId)
        var changed = true
        while (changed) {
            changed = false
            c.nodes.forEach {
                if (it.parent in toRemove && toRemove.add(it.id)) changed = true
            }
        }
        setConversation(c.copy(nodes = c.nodes.filterNot { it.id in toRemove }))
    }

    fun send(bookId: String, text: String, images: List<String>) {
        val conv = conversation(bookId)
        val parentId = conv.nodes.lastOrNull()?.id

        val userNode = MessageNode(
            id = uid(), parent = parentId, role = "user",
            content = text, images = images, ts = nowMs()
        )
        val aiNode = MessageNode(
            id = uid(), parent = userNode.id, role = "assistant",
            content = "", ts = nowMs()
        )
        appendNodes(bookId, listOf(userNode, aiNode))
        busyNodes = busyNodes + aiNode.id

        viewModelScope.launch {
            try {
                val s = data.settings
                val provider = s.providers.firstOrNull { it.id == s.providerId }
                    ?: throw IllegalStateException("尚未配置模型服务商，请到设置中添加")
                if (s.model.isBlank()) throw IllegalStateException("尚未选择模型")
                if (provider.baseUrl.isBlank() || provider.baseUrl == "https://")
                    throw IllegalStateException("请先填写服务商 Base URL")

                val tpl = templateOf(s.templateId)
                val sys = buildString {
                    if (s.autoTemplate && tpl != null && tpl.content.isNotBlank()) append(tpl.content)
                    if (s.systemPrompt.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n---\n\n")
                        append(s.systemPrompt)
                    }
                }

                val freshConv = conversation(bookId)
                val path = buildPath(freshConv.nodes, userNode.id)

                val result = Api.chat(
                    provider = provider,
                    model = s.model,
                    systemPrompt = sys,
                    history = path,
                    temperature = s.temperature,
                    maxTokens = s.maxTokens
                )

                updateNode(bookId, aiNode.id) {
                    it.copy(content = result.content, reasoning = result.reasoning, error = null)
                }
            } catch (e: Exception) {
                updateNode(bookId, aiNode.id) {
                    it.copy(error = e.message ?: "请求失败", content = it.content)
                }
            } finally {
                busyNodes = busyNodes - aiNode.id
            }
        }
    }

    // ---------------- 设置 ----------------

    fun templateOf(id: String): Template? = data.settings.templates.firstOrNull { it.id == id }

    fun currentProvider(): Provider? =
        data.settings.providers.firstOrNull { it.id == data.settings.providerId }

    fun saveSettings(s: Settings) {
        commit(data.copy(settings = s))
    }
}
