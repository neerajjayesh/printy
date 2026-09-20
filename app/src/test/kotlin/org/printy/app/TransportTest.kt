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
            RawTransport(writeTimeoutMs = 300).use { transport ->
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

    @Test(timeout = 15000) fun drainsRepliesWhileSendingAndDeliversFooterBeforeEof() {
        val executor = Executors.newSingleThreadExecutor()
        val body = ByteArray(512 * 1024) { (it % 251).toByte() }
        val footer = byteArrayOf(12, 27, 64)
        val reply = ByteArray(512 * 1024) { 65 }
        try { ServerSocket(0).use { server ->
            // A bidirectional bridge can write replies before it consumes more print data.
            val received = executor.submit<ByteArray> { server.accept().use { peer ->
                peer.soTimeout = 5000; peer.sendBufferSize = 8192
                peer.getOutputStream().write(reply)
                peer.getInputStream().readBytes()
            } }
            RawTransport(writeTimeoutMs = 5000).use { transport ->
                val output = java.io.BufferedOutputStream(transport.connect("127.0.0.1", server.localPort))
                output.write(body); output.write(footer); output.flush()
                assertEquals(RawTransport.FinishResult.SERVER_CLOSED, transport.finish(5000))
                assertArrayEquals(body + footer, received.get(5, TimeUnit.SECONDS))
                assertEquals((body.size + footer.size).toLong(), transport.bytesWritten)
                assertEquals(reply.size.toLong(), transport.bytesReceived)
            }
        } } finally { executor.shutdownNow() }
    }

    @Test(timeout = 10000) fun finishWaitsForServerToCloseAfterOutputEof() {
        val executor = Executors.newFixedThreadPool(2)
        val receivedEof = CountDownLatch(1)
        val releaseServer = CountDownLatch(1)
        try { ServerSocket(0).use { server ->
            val peerTask = executor.submit { server.accept().use { peer ->
                peer.soTimeout = 5000
                assertArrayEquals(byteArrayOf(12), peer.getInputStream().readBytes())
                receivedEof.countDown()
                assertTrue(releaseServer.await(5, TimeUnit.SECONDS))
                peer.getOutputStream().write(byteArrayOf(65, 66))
            } }
            RawTransport().use { transport ->
                transport.connect("127.0.0.1", server.localPort).write(12)
                val finishing = executor.submit<RawTransport.FinishResult> { transport.finish(5000) }
                assertTrue(receivedEof.await(3, TimeUnit.SECONDS))
                assertFalse("Do not finish while the server is still draining", finishing.isDone)
                releaseServer.countDown()
                assertEquals(RawTransport.FinishResult.SERVER_CLOSED, finishing.get(3, TimeUnit.SECONDS))
                peerTask.get(3, TimeUnit.SECONDS)
                assertEquals(2L, transport.bytesReceived)
            }
        } } finally { releaseServer.countDown(); executor.shutdownNow() }
    }

    @Test(timeout = 5000) fun finishIsBoundedWhenAQuietServerKeepsItsSideOpen() {
        ServerSocket(0).use { server -> RawTransport().use { transport ->
            transport.connect("127.0.0.1", server.localPort).write(12)
            server.accept().use { peer ->
                assertEquals(RawTransport.FinishResult.WAIT_EXPIRED, transport.finish(100))
                peer.soTimeout = 1000
                assertArrayEquals(byteArrayOf(12), peer.getInputStream().readBytes())
            }
        } }
    }

    @Test(timeout = 5000) fun cancellationClosesAConnectionWaitingForServerEof() {
        val executor = Executors.newSingleThreadExecutor()
        try { ServerSocket(0).use { server -> RawTransport().use { transport ->
            transport.connect("127.0.0.1", server.localPort).write(12)
            server.accept().use { peer ->
                peer.soTimeout = 1000
                val finishing = executor.submit<Boolean> {
                    try { transport.finish(60000); false } catch (_: java.io.IOException) { true }
                }
                assertArrayEquals(byteArrayOf(12), peer.getInputStream().readBytes())
                transport.close()
                assertTrue(finishing.get(2, TimeUnit.SECONDS))
            }
        } } } finally { executor.shutdownNow() }
    }

    @Test(timeout = 5000) fun serverResetDuringFinishIsNotReportedAsSent() {
        val executor = Executors.newSingleThreadExecutor()
        try { ServerSocket(0).use { server -> RawTransport().use { transport ->
            transport.connect("127.0.0.1", server.localPort).write(12)
            server.accept().use { peer ->
                peer.soTimeout = 1000
                val finishing = executor.submit<Boolean> {
                    try { transport.finish(3000); false } catch (_: java.io.IOException) { true }
                }
                assertArrayEquals(byteArrayOf(12), peer.getInputStream().readBytes())
                peer.setSoLinger(true, 0); peer.close()
                assertTrue(finishing.get(2, TimeUnit.SECONDS))
            }
        } } } finally { executor.shutdownNow() }
    }
}
