package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class RenderedPdfPage(
    val bitmap: Bitmap,
    val pageIndex: Int,
    val totalPages: Int
)

object PdfBitmapHelper {

    private fun ensureAssetPdfCached(context: Context, assetName: String = "tasniremt_source.pdf"): File {
        val outFile = File(context.cacheDir, assetName)
        if (!outFile.exists() || outFile.length() == 0L) {
            context.assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile
    }

    suspend fun renderAssetPdfPage(
        context: Context,
        pageIndexZeroBased: Int,
        assetName: String = "tasniremt_source.pdf"
    ): Result<RenderedPdfPage> = withContext(Dispatchers.IO) {
        runCatching {
            val file = ensureAssetPdfCached(context, assetName)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                renderFromPfd(pfd, pageIndexZeroBased)
            }
        }
    }

    suspend fun renderUriPdfPage(
        context: Context,
        uri: Uri,
        pageIndexZeroBased: Int
    ): Result<RenderedPdfPage> = withContext(Dispatchers.IO) {
        runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Could not open selected PDF file.")
            pfd.use {
                renderFromPfd(it, pageIndexZeroBased)
            }
        }
    }

    private fun renderFromPfd(pfd: ParcelFileDescriptor, requestedPage: Int): RenderedPdfPage {
        PdfRenderer(pfd).use { renderer ->
            val count = renderer.pageCount.coerceAtLeast(1)
            val safeIndex = requestedPage.coerceIn(0, count - 1)
            renderer.openPage(safeIndex).use { page ->
                val targetWidth = 1440
                val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1).toFloat()
                val targetHeight = (page.height * scale).toInt().coerceIn(400, 2400)
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return RenderedPdfPage(
                    bitmap = bitmap,
                    pageIndex = safeIndex,
                    totalPages = count
                )
            }
        }
    }

    suspend fun decodeUriBitmap(context: Context, uri: Uri): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxSide = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            var sampleSize = 1
            while (maxSide / sampleSize > 2048) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: throw IllegalStateException("Unable to decode selected image.")
            decoded
        }
    }

    fun createCameraCaptureFile(context: Context): File {
        val dir = File(context.cacheDir, "ocr_captures").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }
}
