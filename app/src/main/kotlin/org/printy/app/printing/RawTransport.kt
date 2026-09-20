// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.printing

import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Closing the handle immediately interrupts a blocked connect/write, including cancellation. */
class RawTransport(private val writeTimeoutMs: Long = 120_000) : Closeable {
    enum class FinishResult { SERVER_CLOSED, WAIT_EXPIRED }
    private val socket = Socket()
    private val closed = AtomicBoolean(false)
    private val timedOut = AtomicBoolean(false)
    private val writeStarted = AtomicLong(0)
    private val sent = AtomicLong(0)
    private val received = AtomicLong(0)
    private val receiveFailure = AtomicReference<IOException?>(null)
    private val readerDone = CountDownLatch(1)
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "printer-timeout").apply { isDaemon = true } }
    private val reader = Executors.newSingleThreadExecutor { r -> Thread(r, "printer-replies").apply { isDaemon = true } }
    val bytesWritten: Long get() = sent.get()
    val bytesReceived: Long get() = received.get()

    fun connect(host: String, port: Int, timeoutMs: Int = 5000): OutputStream {
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        socket.tcpNoDelay = true
        // Drain replies during sending as well as shutdown. Unread input can cause a TCP
        // reset on close, and a server blocked writing replies may stop reading the job.
        // Replies are counted, never treated as proof of printing or retained as document data.
        reader.execute {
            try {
                val input = socket.getInputStream()
                val buffer = ByteArray(4096)
                while (!closed.get()) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    received.addAndGet(count.toLong())
                }
            } catch (e: IOException) {
                if (!closed.get()) receiveFailure.compareAndSet(null, e)
            } finally { readerDone.countDown() }
        }
        watchdog.scheduleWithFixedDelay({
            val start = writeStarted.get()
            if (start != 0L && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) > writeTimeoutMs) {
                timedOut.set(true); runCatching { socket.close() }
            }
        }, 250, 250, TimeUnit.MILLISECONDS)
        val sink = socket.getOutputStream()
        return object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()))
            override fun write(b: ByteArray, off: Int, len: Int) {
                checkConnection()
                writeStarted.set(System.nanoTime())
                try { sink.write(b, off, len); sent.addAndGet(len.toLong()) }
                catch (e: IOException) {
                    if (timedOut.get()) throw SocketTimeoutException("Printer stopped accepting data")
                    throw e
                } finally { writeStarted.set(0) }
            }
            override fun flush() = sink.flush()
        }
    }

    /** Call only after the complete page/footer has been flushed. Cancellation uses close().
     * Half-close tells the server there are no more job bytes, while allowing it to drain
     * queued output and send replies before the socket is fully closed. Neither result
     * confirms physical printing; some raw servers never close their side at all.
     */
    fun finish(timeoutMs: Long = 90_000): FinishResult {
        require(timeoutMs > 0)
        checkConnection()
        socket.getOutputStream().flush()
        socket.shutdownOutput()
        val ended = readerDone.await(timeoutMs, TimeUnit.MILLISECONDS)
        checkConnection()
        return if (ended) FinishResult.SERVER_CLOSED else FinishResult.WAIT_EXPIRED
    }

    private fun checkConnection() {
        if (timedOut.get()) throw SocketTimeoutException("Printer stopped accepting data")
        if (closed.get()) throw SocketException("Print connection closed")
        receiveFailure.get()?.let { throw it }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { socket.close() }
            readerDone.countDown() // Wake finish() immediately, even if canceled before connect.
            watchdog.shutdownNow(); reader.shutdownNow()
        }
    }
    companion object {
        fun reachable(host: String, port: Int): Boolean = runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), 1000) }; true
        }.getOrDefault(false)
    }
}
