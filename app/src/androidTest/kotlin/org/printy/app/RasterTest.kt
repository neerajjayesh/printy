// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.printy.app.printing.*
import org.printy.escp.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class RasterTest {
    @Test fun stripsPreserveGeometryAcrossStripBoundaries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("raster-test", ".pdf", context.cacheDir)
        try {
            PdfDocument().useDocument { pdf ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 600, 1).create())
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawRect(0f, 0f, 200f, 600f, Paint().apply { color = Color.BLACK })
                pdf.finishPage(page); file.outputStream().use(pdf::writeTo)
            }
            val spec = PageSpec(Paper.PHOTO_4X6, EpsonModel.byId("l130"))
            Documents.renderer(file).use { renderer -> renderer.openPage(0).use { page ->
                Documents.PageRaster(page, spec, PrintSettings(paper = Paper.PHOTO_4X6)).use { source ->
                    val row = IntArray(source.width)
                    for (y in listOf(127, 128, 129, 1024, 1900)) {
                        source.readRow(y, row)
                        assertEquals(Color.BLACK, row[source.width / 4])
                        assertEquals(Color.WHITE, row[source.width * 3 / 4])
                    }
                }
            } }
        } finally { file.delete() }
    }
}
