// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.escp

import java.io.ByteArrayOutputStream

/** TIFF PackBits, the compressed raster representation used by ESC i. */
object PackBits {
    fun encode(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < input.size) {
            var run = 1
            while (i + run < input.size && run < 128 && input[i + run] == input[i]) run++
            if (run >= 3) {
                out.write(257 - run); out.write(input[i].toInt()); i += run
            } else {
                val start = i
                i += run
                while (i < input.size && i - start < 128) {
                    var next = 1
                    while (i + next < input.size && next < 3 && input[i + next] == input[i]) next++
                    if (next == 3) break
                    i += minOf(next, 128 - (i - start))
                }
                out.write(i - start - 1); out.write(input, start, i - start)
            }
        }
        return out.toByteArray()
    }
}
