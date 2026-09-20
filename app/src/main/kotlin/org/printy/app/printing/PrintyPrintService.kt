// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.printing

import android.app.PendingIntent
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.print.*
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import kotlinx.coroutines.*
import org.printy.app.MainActivity
import org.printy.app.PrintyApplication
import org.printy.app.data.PrinterProfile
import org.printy.escp.Paper

class PrintyPrintService : PrintService() {
    private val app get() = application as PrintyApplication
    private val submitted = mutableSetOf<String>()
    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession = Discovery()
    override fun onConnected() {
        // Never silently replay a partially sent job after process death.
        activePrintJobs.forEach { job ->
            if (!app.jobs.contains(job.id.toString())) {
                if (job.isQueued) onPrintJobQueued(job)
                else job.fail("Printing was interrupted. Some pages may have printed. Check the printer before retrying.")
            }
        }
    }
    override fun onPrintJobQueued(printJob: PrintJob) {
        val id = printJob.id.toString()
        if (app.jobs.contains(id)) return
        val info = printJob.info
        val printer = app.printers.printers.value.find { it.id == info.printerId?.localId }
        if (printer == null) { printJob.fail("This printer was removed. Add it in Printy and try again."); return }
        val attrs = info.attributes
        val media = attrs.mediaSize
        val portrait = media?.asPortrait()
        val paper = Paper.entries.find { p -> portrait != null && kotlin.math.abs(p.widthMils - portrait.widthMils) < 20 && kotlin.math.abs(p.heightMils - portrait.heightMils) < 20 }
        if (paper == null || info.copies !in 1..99 || (attrs.duplexMode != 0 && attrs.duplexMode != PrintAttributes.DUPLEX_MODE_NONE)) {
            printJob.fail("Choose A4, Letter or 4 × 6 paper, one-sided printing and 1–99 copies."); return
        }
        if (attrs.resolution?.horizontalDpi != 360 || attrs.resolution?.verticalDpi != 360) {
            printJob.fail("Choose the standard print quality and try again."); return
        }
        val fd = printJob.document.data
        if (fd == null) { printJob.fail("The document wasn't ready. Open it and print again."); return }
        submitted.add(id)
        app.jobs.submit(id, info.label, printer,
            PrintSettings(paper, info.copies, attrs.colorMode == PrintAttributes.COLOR_MODE_MONOCHROME, media?.isPortrait == false, systemLayout = true),
            prepare = {
                // Android's spooler supplies the selected pages as a new PDF; do not apply
                // PrintJobInfo.pages a second time (indices refer to the original document).
                val file = Documents.copy(this, ParcelFileDescriptor.AutoCloseInputStream(fd))
                try { withContext(Dispatchers.IO) { Documents.inspect(file, info.label) } }
                catch (e: Throwable) { file.delete(); throw e }
            }, cancelInput = { runCatching { fd.close() } }, onStarted = { printJob.start() },
            onProgress = { state -> printJob.setStatus(state.message); printJob.setProgress(state.progress) },
            onFinished = { state ->
                submitted.remove(id)
                when (state.phase) {
                    JobPhase.SENT -> printJob.complete()
                    JobPhase.CANCELED -> printJob.cancel()
                    else -> printJob.fail(state.message)
                }
            })
    }
    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        if (app.jobs.contains(printJob.id.toString())) app.jobs.cancel(printJob.id.toString()) else printJob.cancel()
    }
    override fun onDestroy() { submitted.toList().forEach(app.jobs::cancel); super.onDestroy() }

    private inner class Discovery : PrinterDiscoverySession() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var poll: Job? = null
        private var observing: Job? = null
        private val tracked = mutableSetOf<PrinterId>()
        private var discovering = false
        private val reachability = mutableMapOf<String, Boolean>()
        init {
            observing = scope.launch { app.printers.printers.collect { profiles ->
                val ids = profiles.map { generatePrinterId(it.id) }
                removePrinters(printers.map { it.id }.filterNot { it in ids })
                addPrinters(profiles.map(::info))
            } }
        }
        private fun info(p: PrinterProfile): PrinterInfo {
            val id = generatePrinterId(p.id)
            val caps = PrinterCapabilitiesInfo.Builder(id)
                .setMinMargins(PrintAttributes.Margins(125, 125, 125, 125))
                .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
                .addMediaSize(PrintAttributes.MediaSize("photo_4x6", "Photo 4 × 6 in", 4000, 6000), false)
                .addResolution(PrintAttributes.Resolution("standard", "Standard", 360, 360), true)
                .setColorModes(PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE, PrintAttributes.DUPLEX_MODE_NONE).build()
            val status = if (reachability[p.id] == false) PrinterInfo.STATUS_UNAVAILABLE else PrinterInfo.STATUS_IDLE
            val settings = PendingIntent.getActivity(this@PrintyPrintService, 0, Intent(this@PrintyPrintService, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            return PrinterInfo.Builder(id, p.name, status).setDescription(p.endpoint).setCapabilities(caps).setInfoIntent(settings).build()
        }
        private suspend fun refresh(ids: List<PrinterId>? = null) {
            if (app.jobs.states.value.any { it.active }) return
            val profiles = app.printers.printers.value.filter { ids == null || generatePrinterId(it.id) in ids }
            profiles.forEach { p ->
                val online = app.jobs.reachable(p)
                reachability[p.id] = online
                if (app.printers.printers.value.any { it.id == p.id }) addPrinters(listOf(info(p)))
            }
        }
        private fun updatePolling() {
            poll?.cancel()
            if (discovering || tracked.isNotEmpty()) poll = scope.launch {
                while (isActive) { refresh(if (discovering) null else tracked.toList()); delay(15_000) }
            }
        }
        override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) { discovering = true; updatePolling() }
        override fun onStopPrinterDiscovery() { discovering = false; updatePolling() }
        override fun onValidatePrinters(printerIds: MutableList<PrinterId>) { scope.launch { refresh(printerIds) } }
        override fun onStartPrinterStateTracking(printerId: PrinterId) { tracked.add(printerId); updatePolling() }
        override fun onStopPrinterStateTracking(printerId: PrinterId) { tracked.remove(printerId); updatePolling() }
        override fun onDestroy() { scope.cancel() }
    }
}
