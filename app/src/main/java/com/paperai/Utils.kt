// ==================== app/src/main/java/com/paperai/Utils.kt ====================
package com.paperai

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID

fun uid(): String = UUID.randomUUID().toString()
fun nowMs(): Long = System.currentTimeMillis()

fun Context.copyUriToCache(uri: Uri): String? = try {
    val f = File(cacheDir, "pick_${System.currentTimeMillis()}.jpg")
    contentResolver.openInputStream(uri)?.use { input ->
        f.outputStream().use { input.copyTo(it) }
    }
    f.absolutePath
} catch (e: Exception) {
    null
}

fun loadOrientedBitmap(path: String, maxDim: Int = 2200): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2

    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    var bmp = BitmapFactory.decodeFile(path, opts) ?: return null

    val deg = try {
        when (ExifInterface(path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }

    if (deg != 0f) {
        val m = Matrix().apply { postRotate(deg) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated != bmp) {
            bmp.recycle()
            bmp = rotated
        }
    }
    return bmp
}

fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
    val m = Matrix().apply { postRotate(degrees) }
    val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    if (out != src) src.recycle()
    return out
}

/** 把四边形映射后的结果导出为 JPEG 文件 */
fun exportQuad(src: Bitmap, quad: List<Offset>, outFile: File, quality: Int = 88): Boolean = try {
    val w = src.width.toFloat()
    val h = src.height.toFloat()
    val dst = floatArrayOf(
        quad[0].x * w, quad[0].y * h,
        quad[1].x * w, quad[1].y * h,
        quad[2].x * w, quad[2].y * h,
        quad[3].x * w, quad[3].y * h
    )
    val xs = floatArrayOf(dst[0], dst[2], dst[4], dst[6])
    val ys = floatArrayOf(dst[1], dst[3], dst[5], dst[7])
    val minX = xs.min()
    val maxX = xs.max()
    val minY = ys.min()
    val maxY = ys.max()
    val ow = (maxX - minX).toInt().coerceAtLeast(1)
    val oh = (maxY - minY).toInt().coerceAtLeast(1)

    val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(Color.WHITE)
    val m = Matrix()
    m.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, dst, 0, 4)
    m.postTranslate(-minX, -minY)
    c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))

    outFile.parentFile?.mkdirs()
    outFile.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    out.recycle()
    true
} catch (e: Exception) {
    false
}

/** 构建从根到 leaf 的消息链 */
fun buildPath(nodes: List<MessageNode>, leafId: String?): List<MessageNode> {
    if (leafId == null) return emptyList()
    val map = nodes.associateBy { it.id }
    val out = ArrayList<MessageNode>()
    var cur = map[leafId]
    var guard = 0
    while (cur != null && guard++ < 1000) {
        out.add(cur)
        cur = cur.parent?.let { map[it] }
    }
    out.reverse()
    return out
}
