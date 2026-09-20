// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app

import org.junit.Assert.*
import org.junit.Test
import org.printy.app.data.ProfileValidation
import org.printy.app.printing.RawTransport
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.*

class TransportTest {
    @Test fun validatesNumericAddressesAndPorts() {
        assertNull(ProfileValidation.error("Office", "192.168.1.1", "9100"))
        assertNull(ProfileValidation.error("Office", "::1", "9100"))
        for (host in listOf("printer.local", "256.1.1.1", "1.2.3", "", "1.2.3.-1")) assertFalse(ProfileValidation.isAddress(host))
        assertNotNull(ProfileValidation.error("Office", "127.0.0.1", "65536"))
        assertNotNull(ProfileValidation.error("", "127.0.0.1", "9100"))
    }
    @Test fun streamsBytesToLocalPrinter() {
        val executor = Executors.newSingleThreadExecutor()
        try { ServerSocket(0).use { server ->
            val received = executor.submit<ByteArray> { server.accept().use { it.getInputStream().readBytes() } }
            RawTransport().use { transport -> transport.connect("127.0.0.1", server.localPort).write(byteArrayOf(27, 64, 12)) }
            assertArrayEquals(byteArrayOf(27, 64, 12), received.get(3, TimeUnit.SECONDS))
        } } finally { executor.shutdownNow() }
    }
    @Test fun cancellationClosesBlockedWriter() {
        val executor = Executors.newSingleThreadExecutor()
        try { ServerSocket(0).use { server ->
            server.receiveBufferSize = 1024
            RawTransport().use { transport ->
                val output = transport.connect("127.0.0.1", server.localPort)
                server.accept().use { peer ->
                    peer.receiveBufferSize = 1024
                    val writer = executor.submit<Boolean> {
                        try { repeat(1024) { output.write(ByteArray(1024 * 1024)) }; false }
                        catch (_: java.io.IOException) { true }
                    }
                    transport.close()
                    assertTrue(writer.get(3, TimeUnit.SECONDS))
                }
            }
        } } finally { executor.shutdownNow() }
    }
    @Test fun stalledPrinterHasAWriteDeadline() {
        val executor = Executors.newSingleThreadExecutor()
        try { ServerSocket(0).use { server ->
            server.receiveBufferSize = 1024
            RawTransport(idleTimeoutMs = 300).use { transport ->
                val output = transport.connect("127.0.0.1", server.localPort)
                server.accept().use { peer ->
                    peer.receiveBufferSize = 1024
                    val writer = executor.submit<Boolean> {
                        try { repeat(1024) { output.write(ByteArray(1024 * 1024)) }; false }
                        catch (_: java.net.SocketTimeoutException) { true }
                    }
                    assertTrue(writer.get(5, TimeUnit.SECONDS))
                }
            }
        } } finally { executor.shutdownNow() }
    }
}
