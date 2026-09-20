// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.printing

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.printy.app.BuildConfig
import org.printy.app.data.PrinterProfile
import org.printy.app.data.PrinterStore
import org.printy.escp.*
import java.io.BufferedOutputStream
import java.util.concurrent.ConcurrentHashMap

enum class JobPhase { QUEUED, SENDING, SENT, FAILED, CANCELED }
data class JobState(val id: String, val title: String, val printer: String, val phase: JobPhase,
    val message: String, val page: Int = 0, val total: Int = 0, val progress: Float = 0f,
    val details: String = "") {
    val active get() = phase == JobPhase.QUEUED || phase == JobPhase.SENDING
}

class JobController(private val context: Context, private val printers: PrinterStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = Mutex() // Serialize all output: two jobs can never interleave on one router.
    private val handles = ConcurrentHashMap<String, Handle>()
    private val mutable = MutableStateFlow<List<JobState>>(emptyList())
    val states = mutable.asStateFlow()
    val notifications = JobNotifications(context)
    private class Handle(val cancelInput: () -> Unit) {
        @Volatile var transport: RawTransport? = null
        @Volatile var footerSent = false
        @Volatile var finishResult = "Not started"
        lateinit var job: Job
    }
    // A raw endpoint can still be physically printing after accepting the whole stream.
    // Stop opening automatic probe connections to an endpoint once a job has used it.
    private val attemptedEndpoints = mutableSetOf<String>()
    private val reachability = mutableMapOf<String, Boolean>()
    fun contains(id: String) = handles.containsKey(id)
    suspend fun reachable(printer: PrinterProfile): Boolean = queue.withLock {
        if (printer.endpoint in attemptedEndpoints) return@withLock reachability[printer.endpoint] ?: true
        withContext(Dispatchers.IO) { RawTransport.reachable(printer.host, printer.port) }
            .also { reachability[printer.endpoint] = it }
    }
    fun dismiss(id: String) { mutable.update { list -> list.filterNot { it.id == id && !it.active } }; notifications.dismiss(id) }
    fun cancel(id: String) {
        handles[id]?.let { h -> h.job.cancel(); runCatching(h.cancelInput); h.transport?.close() }
    }
    private fun publish(state: JobState) {
        mutable.update { old -> (old.filterNot { it.id == state.id } + state).takeLast(50) }
        notifications.show(state)
    }

    /** All callbacks run on Main, as required by android.printservice.PrintJob. */
    fun submit(id: String, title: String, printer: PrinterProfile, settings: PrintSettings,
        prepare: suspend () -> LocalDocument, cancelInput: () -> Unit = {},
        onStarted: () -> Boolean = { true }, onProgress: (JobState) -> Unit = {},
        onFinished: (JobState) -> Unit = {}) {
        if (contains(id)) return
        val handle = Handle(cancelInput)
        val initial = JobState(id, title, printer.name, JobPhase.QUEUED, "Waiting to print…")
        handles[id] = handle
        publish(initial)
        handle.job = scope.launch(start = CoroutineStart.LAZY) {
            var owned: LocalDocument? = null
            var terminal: JobState = initial
            val startedAt = System.nanoTime()
            fun details(state: JobState) = buildString {
                appendLine("Printy ${BuildConfig.VERSION_NAME}")
                appendLine("Model: ${printer.modelId}; endpoint: ${printer.endpoint}")
                appendLine("State: ${state.phase}; ${state.message}")
                appendLine("Sheet: ${state.page}/${state.total}; elapsed: ${(System.nanoTime() - startedAt) / 1_000_000_000}s")
                appendLine("Layout: ${settings.layout.label}; selection: ${settings.selection.label}; copies: ${settings.copies}; reverse: ${settings.reverse}")
                appendLine("Bytes written: ${handle.transport?.bytesWritten ?: 0}")
                appendLine("Reply bytes read: ${handle.transport?.bytesReceived ?: 0}")
                appendLine("Page/job ending flushed: ${handle.footerSent}")
                appendLine("Connection finish: ${handle.finishResult}")
                append("These details cannot confirm physical printing or paper ejection.")
            }
            val monitor = launch {
                while (isActive) {
                    delay(1000)
                    mutable.value.find { it.id == id }?.let { state ->
                        val updated = state.copy(details = details(state))
                        publish(updated); onProgress(updated)
                    }
                }
            }
            try {
                queue.withLock {
                    ensureActive()
                    if (!onStarted()) throw CancellationException()
                    val preparing = initial.copy(phase = JobPhase.SENDING, message = "Preparing your document…")
                    publish(preparing); onProgress(preparing)
                    owned = prepare()
                    val document = requireNotNull(owned)
                    val plan = try { PrintPlan.create(document.pages, settings) }
                        catch (e: IllegalArgumentException) { throw UserPrintException(e.message ?: "Choose pages to print.") }
                    val total = plan.sheets.size * settings.copies
                    attemptedEndpoints.add(printer.endpoint)
                    val jobContext = currentCoroutineContext()
                    val power = context.getSystemService(PowerManager::class.java)
                    val wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Printy:printing")
                    wake.acquire(60 * 60 * 1000L)
                    try {
                        withContext(Dispatchers.IO) {
                            // Include local-only Wi-Fi even when Android selects mobile data as its default.
                            @Suppress("DEPRECATION")
                            val connected = context.getSystemService(ConnectivityManager::class.java).allNetworks.isNotEmpty()
                            if (!connected)
                                throw UserPrintException("You're not connected to a network. Connect to the same Wi-Fi as your printer and try again.")
                            RawTransport().use { transport ->
                                handle.transport = transport
                                ensureActive()
                                val output = BufferedOutputStream(transport.connect(printer.host, printer.port), 32 * 1024)
                                withContext(Dispatchers.Main) { reachability[printer.endpoint] = true }
                                val encoder = EscpEncoder(output)
                                val spec = PageSpec(settings.paper, EpsonModel.byId(printer.modelId), settings.grayscale)
                                encoder.beginJob()
                                Documents.renderer(document.file).use { pdf ->
                                    repeat(settings.copies) { copy ->
                                        plan.sheets.forEachIndexed { sheetIndex, sheet ->
                                            ensureActive()
                                            val number = copy * plan.sheets.size + sheetIndex + 1
                                            val state = initial.copy(phase = JobPhase.SENDING, page = number, total = total,
                                                progress = (number - 1).toFloat() / total, message = "Sending sheet $number of $total…")
                                            withContext(Dispatchers.Main) { publish(state); onProgress(state) }
                                            if (settings.layout == SheetLayout.ONE) {
                                                pdf.openPage(requireNotNull(sheet.pages.single())).use { page ->
                                                    Documents.PageRaster(page, spec, settings).use { raster ->
                                                        encoder.page(raster, spec) { jobContext.ensureActive() }
                                                    }
                                                }
                                            } else {
                                                Documents.SheetRaster(pdf, sheet, spec, settings).use { raster ->
                                                    encoder.page(raster, spec) { jobContext.ensureActive() }
                                                }
                                            }
                                            output.flush()
                                        }
                                    }
                                }
                                encoder.endJob()
                                handle.footerSent = true
                                handle.finishResult = "Waiting for server"
                                val finishing = initial.copy(phase = JobPhase.SENDING, page = total, total = total,
                                    progress = 1f, message = "Finishing the connection. Waiting for the print server…")
                                withContext(Dispatchers.Main) { publish(finishing); onProgress(finishing) }
                                handle.finishResult = transport.finish().name
                                ensureActive()
                                // EOF or a bounded wait is not an acknowledgment of physical printing.
                            }
                        }
                    } finally { if (wake.isHeld) wake.release() }
                    printers.markUsed(printer.id)
                    terminal = initial.copy(phase = JobPhase.SENT, message = "Sent to ${printer.name}. Check the printer for your pages.", page = total, total = total, progress = 1f)
                }
            } catch (_: CancellationException) {
                terminal = initial.copy(phase = JobPhase.CANCELED, message = "Printing canceled. Pages already sent may still print.")
            } catch (e: Exception) {
                if (handle.transport?.bytesWritten == 0L) reachability[printer.endpoint] = false
                terminal = initial.copy(phase = JobPhase.FAILED, message = PrintErrors.message(e, printer.endpoint))
            } catch (e: OutOfMemoryError) {
                terminal = initial.copy(phase = JobPhase.FAILED, message = PrintErrors.message(e))
            } finally {
                monitor.cancel()
                handle.transport?.close(); runCatching(cancelInput)
                owned?.file?.delete()
                handles.remove(id)
                withContext(NonCancellable + Dispatchers.Main) {
                    val last = mutable.value.find { it.id == id }
                    if (terminal.phase != JobPhase.SENT && last != null)
                        terminal = terminal.copy(page = last.page, total = last.total, progress = last.progress)
                    terminal = terminal.copy(details = details(terminal))
                    publish(terminal); onFinished(terminal)
                }
            }
        }
        handle.job.start()
    }
}
