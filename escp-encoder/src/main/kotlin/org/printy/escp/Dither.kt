// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.escp

/** Serpentine Floyd–Steinberg diffusion, with model-80 small/large drop levels (0, .28, 1).
 * Inspired by Gutenprint's per-channel error diffusion; no ICC/media calibration is claimed.
 * Two-bit samples are folded into adjacent bits, padded to an eight-pixel boundary.
 */
class Dither(private val width: Int, private val grayscale: Boolean) {
    private var current = Array(4) { DoubleArray(width + 2) }
    private var next = Array(4) { DoubleArray(width + 2) }
    private var row = 0

    fun row(argb: IntArray): Array<ByteArray> {
        require(argb.size >= width)
        val output = Array(4) { ByteArray(2 * ((width + 7) / 8)) }
        val direction = if (row++ % 2 == 0) 1 else -1
        val range = if (direction == 1) 0 until width else width - 1 downTo 0
        for (x in range) {
            val p = argb[x]
            val alpha = (p ushr 24 and 255) / 255.0
            fun component(shift: Int) = 1.0 - alpha + alpha * (p ushr shift and 255) / 255.0
            val r = component(16); val g = component(8); val b = component(0)
            val k = minOf(1 - r, 1 - g, 1 - b)
            for (c in 0 until if (grayscale) 1 else 4) {
                val ink = if (grayscale) 1 - (.2126 * r + .7152 * g + .0722 * b)
                    else when (c) { 0 -> k; 1 -> 1 - r - k; 2 -> 1 - g - k; else -> 1 - b - k }
                val value = (ink + current[c][x + 1]).coerceIn(0.0, 1.0)
                val code = if (value < .14) 0 else if (value < .64) 1 else 3
                val level = if (code == 0) 0.0 else if (code == 1) .28 else 1.0
                val error = value - level
                output[c][x / 4] = (output[c][x / 4].toInt() or (code shl (6 - 2 * (x % 4)))).toByte()
                current[c][x + 1 + direction] += error * 7 / 16
                next[c][x + 1 - direction] += error * 3 / 16
                next[c][x + 1] += error * 5 / 16
                next[c][x + 1 + direction] += error / 16
            }
        }
        val old = current; current = next; next = old
        next.forEach { it.fill(0.0) }
        return output
    }
}
