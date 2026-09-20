// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.printing

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.printy.app.PrintyApplication
import org.printy.escp.Paper
import java.io.File

class DirectPrintService : Service() {
    private val active = mutableSetOf<String>()
    private val app get() = application as PrintyApplication
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("job")
        val printer = app.printers.printers.value.find { it.id == intent?.getStringExtra("printer") }
        if (id == null || printer == null) { stopSelf(startId); return START_NOT_STICKY }
        val title = intent.getStringExtra("title") ?: "Document"
        val state = JobState(id, title, printer.name, JobPhase.QUEUED, "Preparing your document…")
        startForeground(100, app.jobs.notifications.build(state))
        active.add(id)
        val settings = PrintSettings(Paper.valueOf(intent.getStringExtra("paper") ?: "A4"),
            intent.getIntExtra("copies", 1), intent.getBooleanExtra("gray", false), intent.getBooleanExtra("landscape", false))
        val path = intent.getStringExtra("file")
        app.jobs.submit(id, title, printer, settings, prepare = {
            withContext(Dispatchers.IO) {
                if (path == null) Documents.testPage(this@DirectPrintService)
                else {
                    val file = File(path).canonicalFile
                    val root = File(cacheDir, "documents").canonicalFile
                    if (file.parentFile != root) throw UserPrintException("Choose this document again before printing.")
                    val copy = Documents.copy(this@DirectPrintService, file.inputStream())
                    try { Documents.inspect(copy, title) } catch (e: Throwable) { copy.delete(); throw e }
                }
            }
        }, onProgress = { progress -> startForeground(100, app.jobs.notifications.build(progress)) }, onFinished = {
            active.remove(id)
            if (active.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        })
        return START_NOT_STICKY
    }
    override fun onDestroy() { active.toList().forEach(app.jobs::cancel); super.onDestroy() }
}
