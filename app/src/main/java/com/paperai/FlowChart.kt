// ==================== app/src/main/java/com/paperai/FlowChart.kt ====================
package com.paperai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val COL_W = 200.dp
private val ROW_H = 96.dp
private val NODE_W = 172.dp
private val NODE_H = 72.dp

@Composable
fun FlowChartView(
    nodes: List<MessageNode>,
    onNodeClick: (MessageNode) -> Unit,
    modifier: Modifier = Modifier
) {
    if (nodes.isEmpty()) {
        Box(modifier.fillMaxSize(), Alignment.Center) {
            Text("暂无对话记录", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        return
    }

    val layout = remember(nodes) { computeLayout(nodes) }
    val maxCol = layout.values.maxOfOrNull { it.first } ?: 0
    val contentW = COL_W * (maxCol + 1) + 40.dp
    val contentH = ROW_H * nodes.size + 40.dp

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .horizontalScroll(rememberScrollState())
            .verticalScroll(rememberScrollState())
    ) {
        Box(Modifier.size(contentW, contentH)) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val colW = COL_W.toPx()
                val rowH = ROW_H.toPx()
                val nodeW = NODE_W.toPx()
                val nodeH = NODE_H.toPx()
                val pad = 20.dp.toPx()

                nodes.forEach { n ->
                    val p = n.parent ?: return@forEach
                    val from = layout[p] ?: return@forEach
                    val to = layout[n.id] ?: return@forEach
                    val x1 = pad + from.first * colW + nodeW / 2
                    val y1 = pad + from.second * rowH + nodeH
                    val x2 = pad + to.first * colW + nodeW / 2
                    val y2 = pad + to.second * rowH

                    val midY = (y1 + y2) / 2
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x1, y1)
                        cubicTo(x1, midY, x2, midY, x2, y2)
                    }
                    drawPath(
                        path = path,
                        color = Color(0x553F6B5C),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                    )
                }
            }

            nodes.forEach { n ->
                val (col, row) = layout[n.id] ?: (0 to 0)
                Box(
                    Modifier
                        .offset(x = 20.dp + COL_W * col, y = 20.dp + ROW_H * row)
                        .size(NODE_W, NODE_H)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (n.role == "user") MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { onNodeClick(n) }
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = if (n.role == "user") "我" else "AI",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = n.content.ifBlank {
                                n.error ?: if (n.images.isNotEmpty()) "[图片]" else "…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun computeLayout(nodes: List<MessageNode>): Map<String, Pair<Int, Int>> {
    val colOf = HashMap<String, Int>()
    val rowOf = HashMap<String, Int>()
    val childSeen = HashMap<String, Int>()

    nodes.forEachIndexed { index, n ->
        rowOf[n.id] = index
        val p = n.parent
        if (p == null || !colOf.containsKey(p)) {
            colOf[n.id] = 0
        } else {
            val idx = childSeen[p] ?: 0
            childSeen[p] = idx + 1
            colOf[n.id] = colOf[p]!! + idx
        }
    }
    return nodes.associate { it.id to (colOf[it.id]!! to rowOf[it.id]!!) }
}
