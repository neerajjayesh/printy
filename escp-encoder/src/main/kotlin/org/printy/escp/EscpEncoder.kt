// SPDX-License-Identifier: GPL-2.0-or-later
// Command sequencing/model constants derived from Gutenprint; see THIRD_PARTY_NOTICES.md.
package org.printy.escp

import java.io.OutputStream
import java.util.TreeMap

/** Bounded-memory 360 DPI, 2-bit CMYK software weave. Instances are confined to one job. */
class EscpEncoder(private val out: OutputStream) {
    private fun bytes(vararg values: Int) = out.write(values.map { it.toByte() }.toByteArray())
    private fun le(value: Int, count: Int) = ByteArray(count) { (value ushr (8 * it)).toByte() }
    private fun command(letter: Char, vararg values: ByteArray) {
        val size = values.sumOf { it.size }
        bytes(27, 40, letter.code); out.write(le(size, 2)); values.forEach(out::write)
    }
    private fun remote(body: ByteArray) {
        command('R', byteArrayOf(0) + "REMOTE1".toByteArray(Charsets.US_ASCII))
        out.write(body); bytes(27, 0, 0, 0)
    }
    fun beginJob() {
        bytes(0, 0, 0, 27, 1)
        out.write("@EJL 1284.4\n@EJL     \n".toByteArray(Charsets.US_ASCII))
        bytes(27, 64)
        remote(byteArrayOf(80, 77, 2, 0, 0, 0, 83, 78, 3, 0, 0, 0, 1)) // PM; SN plain-paper feed
    }
    fun beginPage(spec: PageSpec) {
        require(spec.model.dpi == 360 && spec.model.nozzlePitch == 2 && spec.model.nozzles == 59 && spec.model.minimumLines == 60) {
            "Only the model-80 360 DPI geometry is implemented."
        }
        command('G', byteArrayOf(1))
        command('U', byteArrayOf(4, 4, 4), le(1440, 2))
        // Keep the same head geometry for gray jobs; only the K plane is emitted.
        command('K', byteArrayOf(0, 2))
        command('i', byteArrayOf(0)) // software weave
        bytes(27, 85, 1) // unidirectional
        command('e', byteArrayOf(0, 16))
        command('D', le(14400, 2), byteArrayOf(80, 40))
        command('C', le(spec.sheetHeight + 120, 4)) // model-80 extraBottom = 24 points
        command('c', le(spec.margin + spec.model.initialVerticalOffset, 4), le(spec.sheetHeight - spec.margin, 4))
        command('S', le(spec.sheetWidth, 4), le(spec.sheetHeight + 120, 4))
    }
    fun raster(color: Int, rows: List<ByteArray>) {
        require(color in listOf(0, 1, 2, 4) && rows.isNotEmpty() && rows.size <= 65535)
        val width = rows.first().size
        require(width in 1..65535 && rows.all { it.size == width })
        bytes(27, 105, color, 1, 2)
        out.write(le(width, 2)); out.write(le(rows.size, 2))
        rows.forEach { out.write(PackBits.encode(it)) }
        bytes(13) // CR is 0x0d (the old manual contains a hex typo).
    }
    /** Every image row is emitted once per channel, with staggered head offsets compensated.
     * 59 nozzles at pitch 2 cover 118 rows using two interleaved passes; row 60 is padding.
     */
    fun page(source: RasterSource, spec: PageSpec, checkpoint: (Int) -> Unit = {}) {
        require(source.width == spec.width && source.height == spec.height)
        beginPage(spec)
        val model = spec.model
        val dither = Dither(source.width, spec.grayscale)
        val pixels = IntArray(source.width)
        val cache = TreeMap<Int, Array<ByteArray>>()
        val blank = ByteArray(2 * ((source.width + 7) / 8))
        val channels = if (spec.grayscale) model.channels.take(1) else model.channels
        val maxOffset = channels.maxOf { it.headOffset }
        val span = model.nozzles * model.nozzlePitch
        var read = 0
        var lastFeed = 0
        for (base in 0 until source.height + maxOffset step span) {
            val needed = minOf(source.height, base + span)
            while (read < needed) {
                checkpoint(read)
                source.readRow(read, pixels); cache[read] = dither.row(pixels); read++
            }
            for (phase in 0 until model.nozzlePitch) {
                val feed = base + phase
                channels.forEachIndexed { c, ink ->
                    checkpoint(minOf(read, source.height - 1))
                    val rows = List(model.minimumLines) { nozzle ->
                        if (nozzle >= model.nozzles) blank
                        else cache[feed + nozzle * model.nozzlePitch - ink.headOffset]?.get(c) ?: blank
                    }
                    if (rows.any { row -> row.any { it != 0.toByte() } }) {
                        command('v', le(feed - lastFeed, 4)); lastFeed = feed
                        command('$', le(spec.margin, 4))
                        raster(ink.command, rows)
                    }
                }
            }
            val oldestNeeded = base + span - maxOffset
            while (cache.isNotEmpty() && cache.firstKey() < oldestNeeded) cache.pollFirstEntry()
        }
        bytes(12)
    }
    fun endJob() {
        bytes(27, 64)
        // Gutenprint restores NVRAM first, then sends the model's postinit JE sequence.
        remote(byteArrayOf(76, 68, 0, 0, 74, 69, 1, 0, 0)) // LD; JE
        out.flush()
    }
}
