// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.printing

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.printy.escp.*
import java.io.Closeable
import java.io.File
import java.io.InputStream

// PdfDocument has close(), but does not implement java.io.Closeable on Android.
inline fun <T> PdfDocument.useDocument(block: (PdfDocument) -> T): T = try { block(this) } finally { close() }

data class LocalDocument(val file: File, val name: String, val pages: Int)
data class PrintSettings(val paper: Paper = Paper.A4, val copies: Int = 1,
    val grayscale: Boolean = false, val landscape: Boolean = false, val systemLayout: Boolean = false) {
    init { require(copies in 1..99) }
}

object Documents {
    private fun temp(context: Context, suffix: String): File {
        val dir = File(context.cacheDir, "documents").apply { mkdirs() }
        return File.createTempFile("print-", suffix, dir)
    }
    suspend fun import(context: Context, uri: Uri): LocalDocument = withContext(Dispatchers.IO) {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "Document"
        val input = context.contentResolver.openInputStream(uri) ?: throw UserPrintException("This file could not be opened. Choose it again.")
        val source = copy(context, input)
        try {
            if (source.inputStream().use { val header = ByteArray(5); it.read(header); String(header, Charsets.US_ASCII) } == "%PDF-") inspect(source, name)
            else {
                val pdf = imageToPdf(context, source)
                source.delete()
                try { inspect(pdf, name) } catch (e: Throwable) { pdf.delete(); throw e }
            }
        } catch (e: Throwable) { source.delete(); throw e }
    }
    suspend fun copy(context: Context, stream: InputStream): File = withContext(Dispatchers.IO) {
        val file = temp(context, ".pdf")
        try {
            stream.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    total += n
                    if (total > 150L * 1024 * 1024) throw UserPrintException("This file is larger than 150 MB. Choose a smaller copy.")
                    output.write(buffer, 0, n)
                }
            } }
            file
        } catch (e: Throwable) { file.delete(); throw e }
    }
    fun inspect(file: File, name: String): LocalDocument = renderer(file).use {
        if (it.pageCount == 0) throw UserPrintException("This document has no pages to print.")
        LocalDocument(file, name, it.pageCount)
    }
    fun renderer(file: File): PdfRenderer {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try { PdfRenderer(fd) } catch (e: Throwable) { fd.close(); throw e }
    }
    private fun imageToPdf(context: Context, source: File): File {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) throw UserPrintException("This file isn't a supported photo or PDF. Export documents as PDF first.")
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 3200) options.inSampleSize *= 2
        val bitmap = BitmapFactory.decodeFile(source.path, options) ?: throw UserPrintException("This photo could not be opened. Try saving it as JPEG or PNG.")
        val file = temp(context, ".pdf")
        try {
            val exif = runCatching { ExifInterface(source) }.getOrNull()
            val rotation = exif?.rotationDegrees ?: 0
            val matrix = Matrix().apply {
                postRotate(rotation.toFloat())
                if (exif?.isFlipped == true) postScale(-1f, 1f)
            }
            val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            matrix.mapRect(bounds); matrix.postTranslate(-bounds.left, -bounds.top)
            PdfDocument().useDocument { pdf ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(bounds.width().toInt(), bounds.height().toInt(), 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                pdf.finishPage(page); file.outputStream().use(pdf::writeTo)
            }
            return file
        } catch (e: Throwable) { file.delete(); throw e } finally { bitmap.recycle() }
    }
    fun testPage(context: Context): LocalDocument {
        val file = temp(context, ".pdf")
        PdfDocument().useDocument { pdf ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val c = page.canvas; c.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 40f; typeface = Typeface.DEFAULT_BOLD }
            c.drawText("Hello from Printy.", 48f, 100f, paint)
            paint.typeface = Typeface.DEFAULT; paint.textSize = 16f
            c.drawText("A little page. A working connection.", 48f, 137f, paint)
            c.drawText("Check that all four colors line up below.", 48f, 167f, paint)
            listOf(Color.BLACK, Color.CYAN, Color.MAGENTA, Color.YELLOW).forEachIndexed { i, color ->
                paint.color = color; c.drawRect(48f + i * 125, 220f, 148f + i * 125, 300f, paint)
            }
            paint.color = Color.BLACK; paint.textSize = 14f
            c.drawText("Made for your printer. Free for everyone.", 48f, 380f, paint)
            c.drawText("If this looks right, you're ready to print.", 48f, 408f, paint)
            pdf.finishPage(page); file.outputStream().use(pdf::writeTo)
        }
        return LocalDocument(file, "Printy test page", 1)
    }

    /** Same geometry for preview and printing. System PDFs already contain page margins. */
    private fun transform(page: PdfRenderer.Page, spec: PageSpec, settings: PrintSettings,
        scale: Float = 1f, stripY: Int = 0, includeMargins: Boolean = false): Matrix {
        val w = (if (settings.systemLayout) spec.sheetWidth else spec.width).toFloat()
        val h = (if (settings.systemLayout) spec.sheetHeight else spec.height).toFloat()
        val logicalW = if (settings.landscape) h else w
        val logicalH = if (settings.landscape) w else h
        val s = minOf(logicalW / page.width, logicalH / page.height)
        val dx = (logicalW - page.width * s) / 2
        val dy = (logicalH - page.height * s) / 2
        val inset = (if (settings.systemLayout) -spec.margin else 0) + (if (includeMargins) spec.margin else 0)
        val values = if (settings.landscape) floatArrayOf(0f, -s, w - dy + inset, s, 0f, dx + inset, 0f, 0f, 1f)
            else floatArrayOf(s, 0f, dx + inset, 0f, s, dy + inset, 0f, 0f, 1f)
        for (i in 0..5) values[i] *= scale
        values[5] -= stripY
        return Matrix().apply { setValues(values) }
    }
    class PageRaster(private val page: PdfRenderer.Page, private val spec: PageSpec,
        private val settings: PrintSettings) : RasterSource, Closeable {
        override val width = spec.width
        override val height = spec.height
        private val strip = createBitmap(width, minOf(128, height), Bitmap.Config.ARGB_8888)
        private var stripStart = -1
        override fun readRow(y: Int, argb: IntArray) {
            require(y in 0 until height)
            val start = y / strip.height * strip.height
            if (stripStart != start) {
                strip.eraseColor(Color.WHITE)
                page.render(strip, null, transform(page, spec, settings, stripY = start), PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                stripStart = start
            }
            strip.getPixels(argb, 0, width, 0, y - stripStart, width, 1)
        }
        override fun close() = strip.recycle()
    }
    suspend fun preview(document: LocalDocument, index: Int, settings: PrintSettings): Bitmap = withContext(Dispatchers.IO) {
        renderer(document.file).use { pdf -> pdf.openPage(index).use { page ->
            val spec = PageSpec(settings.paper, EpsonModel.supported.first(), settings.grayscale)
            val scale = 720f / spec.sheetWidth
            val bitmap = createBitmap(720, (spec.sheetHeight * scale).toInt(), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            try {
                page.render(bitmap, null, transform(page, spec, settings, scale, includeMargins = true), PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                if (settings.grayscale) {
                    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
                    // Drawing a bitmap onto itself is undefined; use a separate bounded preview copy.
                    val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    Canvas(bitmap).drawBitmap(copy, 0f, 0f, paint); copy.recycle()
                }
                if (settings.landscape) {
                    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(-90f) }, true)
                    bitmap.recycle()
                    rotated
                } else bitmap
            } catch (e: Throwable) { bitmap.recycle(); throw e }
        } }
    }
}
