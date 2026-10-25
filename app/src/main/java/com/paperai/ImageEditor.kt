// ==================== app/src/main/java/com/paperai/ImageEditor.kt ====================
package com.paperai

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * 图片编辑器：支持旋转 / 裁切 / 拉伸 / 透视。
 * 通过拖动四个角点实现自由四点变换，四个角点向内拖动即为裁切，向外拉伸即为扩展。
 */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    sourcePath: String,
    onCancel: () -> Unit,
    onDone: (String) -> Unit
) {
    val ctx = LocalContext.current

    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var quad by remember {
        mutableStateOf(
            listOf(
                Offset(0f, 0f), Offset(1f, 0f),
                Offset(1f, 1f), Offset(0f, 1f)
            )
        )
    }
    var dragHandle by remember { mutableStateOf(-1) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(sourcePath) {
        loading = true
        bmp = withContext(Dispatchers.IO) { loadOrientedBitmap(sourcePath) }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("图片处理") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        bmp = bmp?.let { rotateBitmap(it, -90f) }
                    }) { Icon(Icons.Default.Refresh, contentDescription = "逆时针旋转") }
                    IconButton(onClick = {
                        bmp = bmp?.let { rotateBitmap(it, 90f) }
                    }) { Icon(Icons.Default.Refresh, contentDescription = "顺时针旋转") }
                    TextButton(onClick = {
                        quad = listOf(
                            Offset(0f, 0f), Offset(1f, 0f),
                            Offset(1f, 1f), Offset(0f, 1f)
                        )
                    }) { Text("重置") }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }
                    Button(
                        onClick = {
                            val b = bmp ?: return@Button
                            saving = true
                            val outFile = File(ctx.filesDir, "images/edit_${System.currentTimeMillis()}.jpg")
                            val ok = exportQuad(b, quad, outFile)
                            saving = false
                            if (ok) onDone(outFile.absolutePath)
                        },
                        enabled = bmp != null && !saving,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (saving) "处理中…" else "完成") }
                }
            }
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .background(Color(0xFF101010))
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                val b = bmp
                if (b == null) {
                    Text("图片加载失败", color = Color.White, modifier = Modifier.align(Alignment.Center))
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val cw = constraints.maxWidth.toFloat()
                        val ch = constraints.maxHeight.toFloat()
                        val rect = remember(b, cw, ch) { fitRect(b.width.toFloat(), b.height.toFloat(), cw, ch) }
                        val handleR = 14.dp

                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .pointerInput(b, rect) {
                                    detectDragGestures(
                                        onDragStart = { pos ->
                                            val pts = quad.map { q ->
                                                Offset(
                                                    rect.left + q.x * rect.width,
                                                    rect.top + q.y * rect.height
                                                )
                                            }
                                            var best = -1
                                            var bestD = Float.MAX_VALUE
                                            pts.forEachIndexed { i, p ->
                                                val d = (p - pos).getDistance()
                                                if (d < bestD) { bestD = d; best = i }
                                            }
                                            dragHandle = if (bestD < 70f) best else -1
                                        },
                                        onDragEnd = { dragHandle = -1 },
                                        onDragCancel = { dragHandle = -1 },
                                        onDrag = { change, _ ->
                                            val h = dragHandle
                                            if (h >= 0 && rect.width > 1f && rect.height > 1f) {
                                                change.consume()
                                                val nx = ((change.position.x - rect.left) / rect.width).coerceIn(-1f, 2f)
                                                val ny = ((change.position.y - rect.top) / rect.height).coerceIn(-1f, 2f)
                                                val list = quad.toMutableList()
                                                list[h] = Offset(nx, ny)
                                                quad = list
                                            }
                                        }
                                    )
                                }
                        ) {
                            // 绘制底图（透视映射）
                            val srcPts = floatArrayOf(
                                0f, 0f,
                                b.width.toFloat(), 0f,
                                b.width.toFloat(), b.height.toFloat(),
                                0f, b.height.toFloat()
                            )
                            val dstPts = floatArrayOf(
                                rect.left + quad[0].x * rect.width, rect.top + quad[0].y * rect.height,
                                rect.left + quad[1].x * rect.width, rect.top + quad[1].y * rect.height,
                                rect.left + quad[2].x * rect.width, rect.top + quad[2].y * rect.height,
                                rect.left + quad[3].x * rect.width, rect.top + quad[3].y * rect.height
                            )
                            

                            drawContext.canvas.nativeCanvas.apply {
                                val saveCount = save()
                                val androidMatrix = android.graphics.Matrix()
                                androidMatrix.setPolyToPoly(
                                    srcPts, 0, dstPts, 0, 4
                                )
                                val paint = android.graphics.Paint().apply {
                                    isFilterBitmap = true
                                    isAntiAlias = true
                                }
                                drawBitmap(b, androidMatrix, paint)
                                restoreToCount(saveCount)
                            }
                            // 四边虚线框
                            val o = quad.map {
                                Offset(
                                    rect.left + it.x * rect.width,
                                    rect.top + it.y * rect.height
                                )
                            }
                            for (i in 0..3) {
                                drawLine(
                                    color = Color(0xFF7CE0B8),
                                    start = o[i],
                                    end = o[(i + 1) % 4],
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            // 四角控制点
                            o.forEach { p ->
                                drawCircle(Color.White, radius = handleR.toPx() * 0.55f, center = p)
                                drawCircle(
                                    Color(0xFF2F6B5A),
                                    radius = handleR.toPx() * 0.55f,
                                    center = p,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        Text(
                            "拖动四角：透视校正 / 裁切 / 拉伸",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun fitRect(w: Float, h: Float, cw: Float, ch: Float): Rect {
    if (w <= 0f || h <= 0f || cw <= 0f || ch <= 0f) return Rect(0f, 0f, 1f, 1f)
    val s = min(cw / w, ch / h) * 0.92f
    val dw = w * s
    val dh = h * s
    val left = (cw - dw) / 2f
    val top = (ch - dh) / 2f
    return Rect(left, top, left + dw, top + dh)
}
