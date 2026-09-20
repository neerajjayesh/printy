// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.escp

import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.*

class EncoderTest {
    private fun hex(s: String) = s.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun `job initialization matches EJL and REMOTE1 reference bytes`() {
        val output = ByteArrayOutputStream(); EscpEncoder(output).beginJob()
        assertContentEquals(hex("""
            00 00 00 1b 01 40 45 4a 4c 20 31 32 38 34 2e 34 0a
            40 45 4a 4c 20 20 20 20 20 0a 1b 40
            1b 28 52 08 00 00 52 45 4d 4f 54 45 31 50 4d 02 00 00 00 53 4e 03 00 00 00 01 1b 00 00 00
        """), output.toByteArray())
    }
    @Test fun `360 dpi photo page header matches reference fields and signed margin`() {
        val output = ByteArrayOutputStream()
        EscpEncoder(output).beginPage(PageSpec(Paper.PHOTO_4X6, EpsonModel.byId("l130")))
        assertContentEquals(hex("""
            1b 28 47 01 00 01
            1b 28 55 05 00 04 04 04 a0 05
            1b 28 4b 02 00 00 02
            1b 28 69 01 00 00
            1b 55 01
            1b 28 65 02 00 00 10
            1b 28 44 04 00 40 38 50 28
            1b 28 43 04 00 e8 08 00 00
            1b 28 63 08 00 3d ff ff ff 43 08 00 00
            1b 28 53 08 00 a0 05 00 00 e8 08 00 00
        """), output.toByteArray())
    }
    @Test fun `raster header uses bytes not pixels and CR not LF`() {
        val output = ByteArrayOutputStream()
        EscpEncoder(output).raster(2, listOf(hex("c0 00")))
        assertContentEquals(hex("1b 69 02 01 02 02 00 01 00 01 c0 00 0d"), output.toByteArray())
    }
    @Test fun `footer restores NVRAM before model 80 job end and leaves remote mode`() {
        val output = ByteArrayOutputStream(); EscpEncoder(output).endJob()
        assertContentEquals(hex("1b 40 1b 28 52 08 00 00 52 45 4d 4f 54 45 31 4c 44 00 00 4a 45 01 00 00 1b 00 00 00"), output.toByteArray())
    }
    @Test fun `a full job ejects the sheet before reset and LD JE footer`() {
        val spec = PageSpec(Paper.A4, EpsonModel.byId("l130"), grayscale = true)
        val source = object : RasterSource {
            override val width = spec.width; override val height = spec.height
            override fun readRow(y: Int, argb: IntArray) {
                argb.fill(-1)
                // Content both halfway down and near the bottom must be followed by FF.
                if (y == height / 2 || y == height - 100) argb[0] = 0xff000000.toInt()
            }
        }
        val output = ByteArrayOutputStream()
        val encoder = EscpEncoder(output)
        encoder.beginJob(); encoder.page(source, spec); encoder.endJob()
        val ending = hex("0c 1b 40 1b 28 52 08 00 00 52 45 4d 4f 54 45 31 4c 44 00 00 4a 45 01 00 00 1b 00 00 00")
        assertContentEquals(ending, output.toByteArray().takeLast(ending.size).toByteArray())
    }
    @Test fun `PackBits reference literal repeat and 128 byte boundaries`() {
        assertContentEquals(hex("fe aa 01 80 00 fd 55"), PackBits.encode(hex("aa aa aa 80 00 55 55 55 55")))
        assertContentEquals(hex("81 00 00 00"), PackBits.encode(ByteArray(129)))
        assertContentEquals(byteArrayOf(), PackBits.encode(byteArrayOf()))
        assertContentEquals(byteArrayOf(127) + ByteArray(128) { it.toByte() }, PackBits.encode(ByteArray(128) { it.toByte() }))
    }
    @Test fun `PackBits round trips arbitrary data without reserved no-op control`() {
        val random = Random(729)
        for (size in listOf(0, 1, 2, 3, 127, 128, 129, 256, 1027)) repeat(12) {
            val raw = ByteArray(size) { if (random.nextBoolean()) 0 else random.nextInt(256).toByte() }
            assertContentEquals(raw, decode(PackBits.encode(raw)))
        }
    }
    @Test fun `CMYK channels and MSB first packing of odd width`() {
        val d = Dither(5, false)
        val planes = d.row(intArrayOf(0xff000000.toInt(), 0xff00ffff.toInt(), 0xffff00ff.toInt(), 0xffffff00.toInt(), 0xff000000.toInt()))
        assertContentEquals(hex("c0 c0"), planes[0])
        assertContentEquals(hex("30 00"), planes[1])
        assertContentEquals(hex("0c 00"), planes[2])
        assertContentEquals(hex("03 00"), planes[3])
    }
    @Test fun `transparent black composites to white and gray never emits color`() {
        val transparent = Dither(8, false).row(IntArray(8))
        assertTrue(transparent.all { p -> p.all { it == 0.toByte() } })
        val gray = Dither(8, true).row(IntArray(8) { 0xff000000.toInt() })
        assertContentEquals(hex("ff ff"), gray[0])
        assertTrue(gray.drop(1).all { p -> p.all { it == 0.toByte() } })
    }
    @Test fun `midtones preserve average ink density and carry diffusion between rows`() {
        val d = Dither(127, true)
        var density = 0.0
        repeat(100) {
            val row = d.row(IntArray(127) { 0xff808080.toInt() })[0]
            for (x in 0 until 127) density += when ((row[x / 4].toInt() ushr (6 - 2 * (x % 4))) and 3) { 1 -> .28; 3 -> 1.0; else -> 0.0 }
        }
        assertTrue(kotlin.math.abs(density / 12700 - (127.0 / 255)) < .015)
    }
    @Test fun `software weave reconstructs every row once across all staggered heads`() {
        val spec = PageSpec(Paper.PHOTO_4X6, EpsonModel.byId("l130"))
        var reads = 0
        val source = object : RasterSource {
            override val width = spec.width; override val height = spec.height
            override fun readRow(y: Int, argb: IntArray) {
                assertEquals(reads++, y)
                argb.fill(-1)
                argb[0] = intArrayOf(0xff000000.toInt(), 0xff00ffff.toInt(), 0xffff00ff.toInt(), 0xffffff00.toInt())[y % 4]
            }
        }
        val out = ByteArrayOutputStream(); EscpEncoder(out).page(source, spec)
        val data = out.toByteArray(); var at = 0; var feed = 0
        val seen = mutableSetOf<Pair<Int, Int>>()
        fun u() = data[at++].toInt() and 255
        fun int32(start: Int) = (0..3).fold(0) { acc, n -> acc or ((data[start + n].toInt() and 255) shl (n * 8)) }
        while (at < data.size) {
            when (val first = u()) {
                12 -> assertEquals(data.size, at)
                27 -> when (val cmd = u()) {
                    40 -> {
                        val letter = u(); val len = u() or (u() shl 8)
                        if (letter == 'v'.code) feed += int32(at)
                        at += len
                    }
                    85 -> u()
                    105 -> {
                        val color = u(); assertEquals(1, u()); assertEquals(2, u())
                        val width = u() or (u() shl 8); val lines = u() or (u() shl 8)
                        assertEquals(2 * ((spec.width + 7) / 8), width); assertEquals(60, lines)
                        val channel = spec.model.channels.indexOfFirst { it.command == color }
                        repeat(lines) { nozzle ->
                            val raw = ByteArrayOutputStream()
                            while (raw.size() < width) {
                                val control = u(); assertNotEquals(128, control)
                                if (control <= 127) repeat(control + 1) { raw.write(u()) }
                                else { val b = u(); repeat(257 - control) { raw.write(b) } }
                            }
                            assertEquals(width, raw.size())
                            if (raw.toByteArray().any { it != 0.toByte() }) {
                                assertTrue(nozzle < 59)
                                val y = feed + nozzle * 2 - spec.model.channels[channel].headOffset
                                assertTrue(y in 0 until spec.height); assertEquals(y % 4, channel)
                                assertTrue(seen.add(y to channel), "Duplicate raster row $y channel $channel")
                                assertEquals(0xc0.toByte(), raw.toByteArray()[0])
                            }
                        }
                        assertEquals(13, u())
                    }
                    else -> fail("Unexpected command $cmd")
                }
                else -> fail("Unexpected byte $first")
            }
        }
        assertEquals(spec.height, reads); assertEquals(spec.height, seen.size)
    }
    @Test fun `cancellation stops before a page can be ejected`() {
        val spec = PageSpec(Paper.PHOTO_4X6, EpsonModel.byId("l130"))
        val source = object : RasterSource {
            override val width = spec.width; override val height = spec.height
            override fun readRow(y: Int, argb: IntArray) { argb.fill(-1) }
        }
        val out = ByteArrayOutputStream()
        assertFailsWith<InterruptedException> { EscpEncoder(out).page(source, spec) { throw InterruptedException() } }
        assertNotEquals(12.toByte(), out.toByteArray().last())
    }
    @Test fun `unknown models and invalid dimensions are rejected`() {
        assertFailsWith<IllegalArgumentException> { EpsonModel.byId("l1300") }
        assertFailsWith<IllegalArgumentException> { EscpEncoder(ByteArrayOutputStream()).raster(0, listOf(byteArrayOf(0), byteArrayOf(0, 1))) }
        assertEquals(3060, Paper.LETTER.widthPixels(360)); assertEquals(4208, Paper.A4.heightPixels(360))
    }
    private fun decode(data: ByteArray): ByteArray {
        var at = 0; val out = ByteArrayOutputStream()
        while (at < data.size) {
            val count = data[at++].toInt() and 255
            assertNotEquals(128, count)
            if (count <= 127) repeat(count + 1) { out.write(data[at++].toInt()) }
            else { val value = data[at++]; repeat(257 - count) { out.write(value.toInt()) } }
        }
        return out.toByteArray()
    }
}
