// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.escp

import kotlin.math.roundToInt

/** Portrait physical sheets. Landscape rotates content, never the paper feed dimensions. */
enum class Paper(val label: String, val widthMils: Int, val heightMils: Int) {
    A4("A4", 8270, 11690), LETTER("Letter", 8500, 11000), PHOTO_4X6("Photo 4 × 6 in", 4000, 6000);
    fun widthPixels(dpi: Int) = (widthMils * dpi / 1000.0).roundToInt()
    fun heightPixels(dpi: Int) = (heightMils * dpi / 1000.0).roundToInt()
}

/** Model 80 / c82 geometry from Gutenprint. These profiles require hardware validation. */
data class EpsonModel(
    val id: String, val label: String,
    val dpi: Int = 360, val nozzles: Int = 59, val minimumLines: Int = 60,
    val nozzlePitch: Int = 2, val initialVerticalOffset: Int = -240,
    val channels: List<InkChannel> = listOf(InkChannel(0, 0), InkChannel(2, 0), InkChannel(1, 120), InkChannel(4, 240))
) {
    companion object {
        val supported = listOf(EpsonModel("l130", "Epson L130"), EpsonModel("l120", "Epson L120"), EpsonModel("l210", "Epson L210"))
        fun byId(id: String) = supported.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("This printer model is not supported yet.")
    }
}
data class InkChannel(val command: Int, val headOffset: Int)
data class PageSpec(val paper: Paper, val model: EpsonModel, val grayscale: Boolean = false) {
    val margin = model.dpi / 8 // 1/8 inch; no borderless capability advertised.
    val sheetWidth = paper.widthPixels(model.dpi)
    val sheetHeight = paper.heightPixels(model.dpi)
    val width = sheetWidth - 2 * margin
    val height = sheetHeight - 2 * margin
}

/** Rows are opaque/transparent ARGB, in left-to-right order. Implementations can render strips. */
interface RasterSource {
    val width: Int
    val height: Int
    fun readRow(y: Int, argb: IntArray)
}
