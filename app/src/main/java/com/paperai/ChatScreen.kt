// ==================== app/src/main/java/com/paperai/ChatScreen.kt ====================
package com.paperai

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    bookId: String,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val book = vm.item(bookId)
    val conv = vm.conversation(bookId)
    val settings = vm.data.settings

    val branch = remember(conv.nodes) {
        buildPath(conv.nodes, conv.nodes.lastOrNull()?.id)
    }

    var input by remember { mutableStateOf("") }
    var pendingImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var showFlow by remember { mutableStateOf(false) }
    var showTemplateMenu by remember { mutableStateOf(false) }
    var editingImage by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    LaunchedEffect(branch.size) {
        if (branch.isNotEmpty()) {
            listState.animateScrollToItem(branch.size - 1)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val p = ctx.copyUriToCache(uri)
            if (p != null) editingImage = p
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            book?.name?.ifBlank { "未命名试卷" } ?: "未命名试卷",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            vm.templateOf(settings.templateId)?.name ?: "无模板",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showTemplateMenu = true }) {
                        Icon(Icons.Default.List, contentDescription = "模板")
                    }
                    IconButton(onClick = { showFlow = !showFlow }) {
                        Icon(Icons.Default.Share, contentDescription = "流程图")
                    }
                    IconButton(onClick = {
                        nameInput = book?.name ?: ""
                        renaming = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "备注")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(Modifier.padding(8.dp)) {
                    if (pendingImages.isNotEmpty()) {
                        Row(
                            Modifier.horizontalScrollCompatRow().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pendingImages.forEach { p ->
                                Box {
                                    AsyncImage(
                                        model = File(p),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    IconButton(
                                        onClick = { pendingImages = pendingImages - p },
                                        modifier = Modifier.align(Alignment.TopEnd).size(22.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = { picker.launch("image/*") }) {
                            Icon(Icons.Default.Add, contentDescription = "添加图片")
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (pendingImages.isEmpty() && branch.isEmpty())
                                        "上传试卷图片，或直接输入要求…"
                                    else "继续追问…"
                                )
                            },
                            maxLines = 5,
                            shape = RoundedCornerShape(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        val sending = branch.lastOrNull()?.let { vm.busyNodes.contains(it.id) } == true
                        FilledIconButton(
                            onClick = {
                                val text = input.trim()
                                if (text.isBlank() && pendingImages.isEmpty()) return@FilledIconButton
                                if (sending) return@FilledIconButton
                                vm.send(bookId, text, pendingImages)
                                input = ""
                                pendingImages = emptyList()
                            },
                            enabled = !sending && (input.isNotBlank() || pendingImages.isNotEmpty())
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "发送")
                        }
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (showFlow) {
                FlowChartView(
                    nodes = conv.nodes,
                    onNodeClick = { node ->
                        val idx = branch.indexOfFirst { it.id == node.id }
                        if (idx >= 0) {
                            showFlow = false
                            scope.launch { listState.animateScrollToItem(idx) }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                if (branch.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("准备就绪", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "点左下角「+」上传试卷照片\n图片可旋转 / 裁切 / 透视校正\n发送后 AI 会按模板自动分析",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(branch, key = { it.id }) { node ->
                            MessageBubble(
                                node = node,
                                busy = vm.busyNodes.contains(node.id),
                                onImageClick = { p -> editingImage = p }
                            )
                        }
                    }
                }
            }
        }
    }

    // 模板选择
    if (showTemplateMenu) {
        AlertDialog(
            onDismissRequest = { showTemplateMenu = false },
            title = { Text("选择分析模板") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScrollColumn()) {
                    settings.templates.forEach { t ->
                        val sel = t.id == settings.templateId
                        TextButton(
                            onClick = {
                                vm.saveSettings(settings.copy(templateId = t.id))
                                showTemplateMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                (if (sel) "✓ " else "") + t.name,
                                modifier = Modifier.fillMaxWidth(),
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplateMenu = false }) { Text("关闭") }
            }
        )
    }

    // 重命名
    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("设置封面备注") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("备注") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameItem(bookId, nameInput.trim())
                    renaming = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("取消") }
            }
        )
    }

    // 图片编辑器
    editingImage?.let { src ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { editingImage = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(Modifier.fillMaxSize()) {
                ImageEditorScreen(
                    sourcePath = src,
                    onCancel = { editingImage = null },
                    onDone = { out ->
                        pendingImages = pendingImages + out
                        editingImage = null
                    }
                )
            }
        }
    }
}

@Composable
private fun Modifier.horizontalScrollCompatRow(): Modifier =
    this.horizontalScroll(rememberScrollState())

@Composable
private fun Modifier.verticalScrollColumn(): Modifier =
    this.verticalScroll(rememberScrollState())

@Composable
fun MessageBubble(
    node: MessageNode,
    busy: Boolean,
    onImageClick: (String) -> Unit
) {
    val isUser = node.role == "user"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp)
        ) {
            if (node.images.isNotEmpty()) {
                node.images.forEach { p ->
                    AsyncImage(
                        model = File(p),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onImageClick(p) }
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (!isUser && node.reasoning.isNotBlank()) {
                ReasoningBlock(node.reasoning)
                Spacer(Modifier.height(8.dp))
            }

            if (node.content.isNotBlank()) {
                Text(node.content, style = MaterialTheme.typography.bodyMedium)
            }

            if (busy) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("思考中…", style = MaterialTheme.typography.labelSmall)
                }
            }

            node.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    "⚠ $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ReasoningBlock(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x16000000))
            .padding(10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "🧠 深度思考",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}
