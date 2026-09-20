// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app

import android.graphics.Color
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.printy.app.printing.*
import org.printy.escp.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class SheetRasterTest {
    private val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.BLACK)
    private val spec = PageSpec(Paper.PHOTO_4X6, EpsonModel.byId("l130"))

    private fun document(test: (LocalDocument) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("sheet-test", ".pdf", context.cacheDir)
        try {
            PdfDocument().useDocument { pdf ->
                colors.forEachIndexed { i, color ->
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 600, i + 1).create())
                    page.canvas.drawColor(color)
                    pdf.finishPage(page)
                }
                file.outputStream().use(pdf::writeTo)
            }
            test(LocalDocument(file, "Colored pages", colors.size))
        } finally { file.delete() }
    }

    @Test fun landscapeTwoUpPlacesFirstPageOnTheLeftWhenViewedAndKeepsBothCells() = document { doc ->
        val settings = PrintSettings(paper = spec.paper, layout = SheetLayout.TWO, landscape = true)
        val sheet = PrintPlan.create(doc.pages, settings).sheets.first()
        Documents.renderer(doc.file).use { pdf -> Documents.SheetRaster(pdf, sheet, spec, settings).use { raster ->
            val row = IntArray(raster.width)
            raster.readRow(raster.height / 4, row)
            assertEquals(Color.RED, row[raster.width / 2])
            raster.readRow(raster.height * 3 / 4, row)
            assertEquals(Color.GREEN, row[raster.width / 2])
            raster.readRow(raster.height / 2, row)
            assertTrue(row.all { it == Color.WHITE })
        } }
        val preview = runBlocking { Documents.preview(doc, sheet, settings) }
        try {
            assertTrue(preview.width > preview.height)
            assertEquals(Color.RED, preview.getPixel(preview.width / 4, preview.height / 2))
            assertEquals(Color.GREEN, preview.getPixel(preview.width * 3 / 4, preview.height / 2))
        } finally { preview.recycle() }
    }

    @Test fun portraitFourUpRetainsNeighboringPagesAcrossStripBoundaries() = document { doc ->
        val settings = PrintSettings(paper = spec.paper, layout = SheetLayout.FOUR)
        val sheet = PrintPlan.create(doc.pages, settings).sheets.single()
        Documents.renderer(doc.file).use { pdf -> Documents.SheetRaster(pdf, sheet, spec, settings).use { raster ->
            val row = IntArray(raster.width)
            for (y in listOf(127, 128, 129, raster.height / 4)) {
                raster.readRow(y, row)
                assertEquals(Color.RED, row[raster.width / 4])
                assertEquals(Color.GREEN, row[raster.width * 3 / 4])
                assertEquals(Color.WHITE, row[raster.width / 2])
            }
            raster.readRow(raster.height * 3 / 4, row)
            assertEquals(Color.BLUE, row[raster.width / 4])
            assertEquals(Color.BLACK, row[raster.width * 3 / 4])
            // Read backwards to ensure the strip cache does not return stale pixels.
            raster.readRow(raster.height / 4, row)
            assertEquals(Color.RED, row[raster.width / 4])
        } }
    }

    @Test fun selectedPagesAndTrailingBlankAppearInBothRasterAndPreview() = document { doc ->
        val settings = PrintSettings(paper = spec.paper, selection = PageSelection.CUSTOM,
            pageRange = "2-4", reverse = true, layout = SheetLayout.FOUR)
        val sheet = PrintPlan.create(doc.pages, settings).sheets.single()
        Documents.renderer(doc.file).use { pdf -> Documents.SheetRaster(pdf, sheet, spec, settings).use { raster ->
            val row = IntArray(raster.width)
            raster.readRow(raster.height / 4, row)
            assertEquals(Color.BLACK, row[raster.width / 4])
            assertEquals(Color.BLUE, row[raster.width * 3 / 4])
            raster.readRow(raster.height * 3 / 4, row)
            assertEquals(Color.GREEN, row[raster.width / 4])
            assertEquals(Color.WHITE, row[raster.width * 3 / 4])
        } }
        val preview = runBlocking { Documents.preview(doc, sheet, settings) }
        try {
            assertEquals(Color.BLACK, preview.getPixel(preview.width / 4, preview.height / 4))
            assertEquals(Color.BLUE, preview.getPixel(preview.width * 3 / 4, preview.height / 4))
            assertEquals(Color.GREEN, preview.getPixel(preview.width / 4, preview.height * 3 / 4))
            assertEquals(Color.WHITE, preview.getPixel(preview.width * 3 / 4, preview.height * 3 / 4))
        } finally { preview.recycle() }
    }

    @Test fun twoUpFinalBlankDoesNotReuseThePreviousSheetPage() = document { doc ->
        val settings = PrintSettings(paper = spec.paper, layout = SheetLayout.TWO, landscape = true,
            selection = PageSelection.CUSTOM, pageRange = "1-3")
        val sheets = PrintPlan.create(doc.pages, settings).sheets
        Documents.renderer(doc.file).use { pdf ->
            sheets.forEachIndexed { i, sheet -> Documents.SheetRaster(pdf, sheet, spec, settings).use { raster ->
                val row = IntArray(raster.width)
                raster.readRow(raster.height / 4, row)
                assertEquals(if (i == 0) Color.RED else Color.BLUE, row[raster.width / 2])
                raster.readRow(raster.height * 3 / 4, row)
                assertEquals(if (i == 0) Color.GREEN else Color.WHITE, row[raster.width / 2])
            } }
        }
    }

    @Test fun grayscalePreviewAndSinglePageSelectionWork() = document { doc ->
        val settings = PrintSettings(paper = spec.paper, grayscale = true,
            selection = PageSelection.CUSTOM, pageRange = "3")
        val sheet = PrintPlan.create(doc.pages, settings).sheets.single()
        val preview = runBlocking { Documents.preview(doc, sheet, settings) }
        try {
            val pixel = preview.getPixel(preview.width / 2, preview.height / 2)
            assertEquals(Color.red(pixel), Color.green(pixel))
            assertEquals(Color.green(pixel), Color.blue(pixel))
            assertTrue(Color.red(pixel) in 1..254)
        } finally { preview.recycle() }
    }
}
