package edu.csuft.sap.data.schedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** 课表背景图解码、裁剪与 App 私有目录持久化。 */
object ScheduleBackgroundStore {

    suspend fun decodeSelected(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val longest = max(width, height)
                if (longest > MAX_DECODE_EDGE) {
                    val ratio = MAX_DECODE_EDGE.toFloat() / longest
                    decoder.setTargetSize(
                        (width * ratio).roundToInt().coerceAtLeast(1),
                        (height * ratio).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            decodeLegacy(context, uri)
        }
    }

    /**
     * 将裁剪视口反算到原图坐标，输出 1080×1920 竖版背景。
     * [offsetX]/[offsetY] 是用户在裁剪视口中拖动图片的像素偏移。
     */
    suspend fun cropAndSave(
        context: Context,
        bitmap: Bitmap,
        viewportWidth: Int,
        viewportHeight: Int,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
    ): String = withContext(Dispatchers.IO) {
        require(viewportWidth > 0 && viewportHeight > 0) { "裁剪区域无效" }
        val baseScale = max(
            viewportWidth.toFloat() / bitmap.width,
            viewportHeight.toFloat() / bitmap.height,
        )
        val displayScale = baseScale * zoom.coerceIn(1f, MAX_ZOOM)
        val sourceWidth = (viewportWidth / displayScale).roundToInt().coerceIn(1, bitmap.width)
        val sourceHeight = (viewportHeight / displayScale).roundToInt().coerceIn(1, bitmap.height)
        val sourceLeft = (
            (bitmap.width - sourceWidth) / 2f - offsetX / displayScale
        ).roundToInt().coerceIn(0, bitmap.width - sourceWidth)
        val sourceTop = (
            (bitmap.height - sourceHeight) / 2f - offsetY / displayScale
        ).roundToInt().coerceIn(0, bitmap.height - sourceHeight)

        val cropped = Bitmap.createBitmap(bitmap, sourceLeft, sourceTop, sourceWidth, sourceHeight)
        val output = Bitmap.createScaledBitmap(cropped, OUTPUT_WIDTH, OUTPUT_HEIGHT, true)
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val file = File(directory, "schedule-background-${UUID.randomUUID()}.jpg")
        try {
            FileOutputStream(file).use { stream ->
                check(output.compress(Bitmap.CompressFormat.JPEG, 92, stream)) { "背景图写入失败" }
            }
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            if (output !== cropped && output !== bitmap) output.recycle()
            if (cropped !== bitmap) cropped.recycle()
        }
        file.absolutePath
    }

    /** 只删除本功能自己写入的文件，绝不操作用户相册原图。 */
    fun deleteOwned(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val directory = File(context.filesDir, DIRECTORY).canonicalFile
            val file = File(path).canonicalFile
            if (file.parentFile == directory && file.isFile) file.delete()
        }
    }

    /** “另存为新课表”时复制一份背景，使两张课表后续替换/删除互不影响。 */
    fun duplicateOwned(context: Context, path: String?): String? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val directory = File(context.filesDir, DIRECTORY).canonicalFile.apply { mkdirs() }
            val source = File(path).canonicalFile
            if (source.parentFile != directory || !source.isFile) return@runCatching null
            val target = File(directory, "schedule-background-${UUID.randomUUID()}.jpg")
            source.copyTo(target, overwrite = false)
            target.absolutePath
        }.getOrNull()
    }

    private fun decodeLegacy(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: error("无法读取所选图片")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "所选图片无法解码" }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_DECODE_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("所选图片无法解码")

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    const val MAX_ZOOM = 4f
    private const val MAX_DECODE_EDGE = 4096
    private const val OUTPUT_WIDTH = 1080
    private const val OUTPUT_HEIGHT = 1920
    private const val DIRECTORY = "schedule_backgrounds"
}
