// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.printing

import java.io.Closeable
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Closing the handle immediately interrupts a blocked connect/write, including cancellation. */
class RawTransport(private val idleTimeoutMs: Long = 30_000) : Closeable {
    private val socket = Socket()
    private val closed = AtomicBoolean(false)
    private val timedOut = AtomicBoolean(false)
    private val writeStarted = AtomicLong(0)
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "printer-timeout").apply { isDaemon = true } }
    fun connect(host: String, port: Int, timeoutMs: Int = 5000): OutputStream {
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.tcpNoDelay = true
        watchdog.scheduleWithFixedDelay({
            val start = writeStarted.get()
            if (start != 0L && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) > idleTimeoutMs) {
                timedOut.set(true); runCatching { socket.close() }
            }
        }, 250, 250, TimeUnit.MILLISECONDS)
        val sink = socket.getOutputStream()
        return object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()))
            override fun write(b: ByteArray, off: Int, len: Int) {
                writeStarted.set(System.nanoTime())
                try { sink.write(b, off, len) }
                catch (e: java.io.IOException) {
                    if (timedOut.get()) throw SocketTimeoutException("Printer stopped accepting data")
                    throw e
                } finally { writeStarted.set(0) }
            }
            override fun flush() = sink.flush()
        }
    }
    override fun close() {
        if (closed.compareAndSet(false, true)) { runCatching { socket.close() }; watchdog.shutdownNow() }
    }
    companion object {
        fun reachable(host: String, port: Int): Boolean = runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), 1000) }; true
        }.getOrDefault(false)
    }
}
