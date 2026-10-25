// ==================== app/src/main/java/com/paperai/LibraryScreen.kt ====================
package com.paperai

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: AppViewModel,
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val folder = remember { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateOf<String?>(null) }

    val dragIdx = remember { mutableStateOf(-1) }
    val pressPos = remember { mutableStateOf(Offset.Zero) }
    val pressToken = remember { mutableStateOf(0L) }
    val lastTapTime = remember { mutableStateOf(0L) }
    val lastTapIdx = remember { mutableStateOf(-1) }

    var renameTarget by remember { mutableStateOf<LibItem?>(null) }
    var moveTarget by remember { mutableStateOf<LibItem?>(null) }
    var newFolderMode by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("") }

    val items = vm.itemsIn(folder.value)
    val itemsState = rememberUpdatedState(items)
    val folderState = rememberUpdatedState(folder.value)
    val gridState = rememberLazyGridState()
    val crumb = vm.pathOf(folder.value)

    // 长按计时
    LaunchedEffect(pressToken.value) {
        val token = pressToken.value
        if (token == 0L) return@LaunchedEffect
        delay(420)
        if (pressToken.value == token) {
            val idx = indexAt(gridState, pressPos.value)
            val list = itemsState.value
            if (idx in list.indices) {
                dragIdx.value = idx
                selected.value = list[idx].id
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (crumb.isEmpty()) "书库"
                        else crumb.joinToString(" / ") { it.name.ifBlank { "未命名" } },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (folder.value != null) {
                        IconButton(onClick = { folder.value = vm.item(folder.value)?.parent }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        folderNameInput = "新建文件夹"
                        newFolderMode = true
                    }) {
                        Icon(Icons.Default.Create, contentDescription = "新建文件夹")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenBook(vm.createBook(folder.value)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建试卷") }
            )
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {

            if (items.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("这里还空着", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点右下角「新建试卷」开始分析，\n或长按空白处新建文件夹",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                val ch = e.changes.firstOrNull() ?: continue
                                when (e.type) {
                                    PointerEventType.Press -> {
                                        pressPos.value = ch.position
                                        pressToken.value = System.currentTimeMillis()
                                    }
                                    PointerEventType.Move -> {
                                        val pos = ch.position
                                        if (dragIdx.value >= 0) {
                                            e.changes.forEach { it.consume() }
                                            val t = indexAt(gridState, pos)
                                            if (t >= 0 && t != dragIdx.value) {
                                                vm.moveItem(folderState.value, dragIdx.value, t)
                                                dragIdx.value = t
                                            }
                                        } else if ((pos - pressPos.value).getDistance() > 42f) {
                                            pressToken.value = 0L
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        val wasDragging = dragIdx.value >= 0
                                        val pos = pressPos.value
                                        val token = pressToken.value
                                        pressToken.value = 0L
                                        dragIdx.value = -1
                                        if (!wasDragging && token != 0L) {
                                            val idx = indexAt(gridState, pos)
                                            val list = itemsState.value
                                            if (idx in list.indices) {
                                                val t = System.currentTimeMillis()
                                                if (t - lastTapTime.value < 420 && lastTapIdx.value == idx) {
                                                    lastTapTime.value = 0L
                                                    val it0 = list[idx]
                                                    if (it0.type == "folder") folder.value = it0.id
                                                    else onOpenBook(it0.id)
                                                } else {
                                                    lastTapTime.value = t
                                                    lastTapIdx.value = idx
                                                    selected.value = list[idx].id
                                                }
                                            } else {
                                                selected.value = null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                    LibraryCell(
                        item = item,
                        selected = selected.value == item.id,
                        dragging = dragIdx.value == index
                    )
                }
            }

            // 选中项操作条
            val selId = selected.value
            if (selId != null) {
                val selItem = vm.item(selId)
                if (selItem != null) {
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                selItem.name.ifBlank { if (selItem.type == "folder") "文件夹" else "未命名" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            )
                            IconButton(onClick = {
                                renameTarget = selItem
                            }) { Icon(Icons.Default.Edit, contentDescription = "重命名") }
                            IconButton(onClick = { moveTarget = selItem }) {
                                Icon(Icons.Default.Share, contentDescription = "移动")
                            }
                            IconButton(onClick = {
                                vm.deleteItem(selItem.id)
                                selected.value = null
                            }) { Icon(Icons.Default.Delete, contentDescription = "删除") }
                            IconButton(onClick = { selected.value = null }) {
                                Icon(Icons.Default.Close, contentDescription = "取消")
                            }
                        }
                    }
                }
            }
        }
    }

    // 重命名对话框
    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("编辑备注") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("备注 / 名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameItem(target.id, text.trim())
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    // 新建文件夹对话框
    if (newFolderMode) {
        AlertDialog(
            onDismissRequest = { newFolderMode = false },
            title = { Text("新建文件夹") },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("文件夹名") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = vm.createFolder(folder.value)
                    vm.renameItem(id, folderNameInput.trim().ifBlank { "新建文件夹" })
                    newFolderMode = false
                }) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = { newFolderMode = false }) { Text("取消") }
            }
        )
    }

    // 移动对话框
    moveTarget?.let { target ->
        val folders = listOf<LibItem?>(null) + vm.allFolders().filter { it.id != target.id }
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("移动到") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScrollCompat()) {
                    folders.forEach { f ->
                        TextButton(
                            onClick = {
                                vm.moveToFolder(target.id, f?.id)
                                moveTarget = null
                                selected.value = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (f == null) "🏠 根目录"
                                else "📁 " + vm.pathOf(f.id).joinToString("/") { it.name.ifBlank { "未命名" } },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveTarget = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.verticalScroll(rememberScrollState())

private fun indexAt(state: LazyGridState, pos: Offset): Int {
    for (info in state.layoutInfo.visibleItemsInfo) {
        val l = info.offset.x.toFloat()
        val t = info.offset.y.toFloat()
        val r = l + info.size.width
        val b = t + info.size.height
        if (pos.x in l..r && pos.y in t..b) return info.index
    }
    return -1
}

@Composable
fun LibraryCell(
    item: LibItem,
    selected: Boolean,
    dragging: Boolean
) {
    val isFolder = item.type == "folder"
    val base = if (isFolder) MaterialTheme.colorScheme.secondaryContainer else bookCoverColor(item.color)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .graphicsLayer {
                scaleX = if (dragging) 1.10f else 1f
                scaleY = if (dragging) 1.10f else 1f
                alpha = if (dragging) 0.88f else 1f
            }
            .clip(RoundedCornerShape(14.dp))
            .background(base)
            .then(
                if (selected) Modifier.border(
                    BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary),
                    RoundedCornerShape(14.dp)
                ) else Modifier
            )
    ) {
        if (isFolder) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .width(42.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(topEnd = 7.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
            )
        } else {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(7.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.15f))
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = if (isFolder) 10.dp else 15.dp,
                    top = if (isFolder) 18.dp else 13.dp,
                    end = 8.dp,
                    bottom = 8.dp
                )
        ) {
            Text(
                text = item.name.ifBlank { if (isFolder) "新建文件夹" else "未命名试卷" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (isFolder) "文件夹" else "试卷",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
