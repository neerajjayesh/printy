// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.printy.app.PrintyApplication
import org.printy.app.data.PrinterProfile
import org.printy.app.printing.*
import java.util.UUID

class PrintyViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as PrintyApplication
    val printers = app.printers.printers
    val jobs = app.jobs.states
    val document = MutableStateFlow<LocalDocument?>(null)
    val settings = MutableStateFlow(PrintSettings())
    val error = MutableStateFlow<String?>(null)
    val importing = MutableStateFlow(false)
    val onboarded = MutableStateFlow(app.printers.onboarded)
    private val mutableOnline = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val online = mutableOnline.asStateFlow()
    private var importJob: Job? = null
    fun import(uri: Uri) {
        importJob?.cancel()
        importJob = viewModelScope.launch {
            importing.value = true; error.value = null
            try { document.value = Documents.import(app, uri) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = PrintErrors.message(e) }
            catch (e: OutOfMemoryError) { error.value = PrintErrors.message(e) }
            finally { importing.value = false }
        }
    }
    suspend fun refresh() {
        if (jobs.value.any { it.active }) return
        val values = printers.value.associate { it.id to app.jobs.reachable(it) }
        mutableOnline.value = values
    }
    fun finishOnboarding() { app.printers.onboarded = true; onboarded.value = true }
    fun print(printer: PrinterProfile, test: Boolean = false): String? {
        val doc = document.value
        if (!test && doc == null) return null
        val s = if (test) PrintSettings() else settings.value
        val id = UUID.randomUUID().toString()
        val intent = Intent(app, DirectPrintService::class.java)
            .putExtra("job", id).putExtra("printer", printer.id).putExtra("title", if (test) "Printy test page" else doc?.name)
            .putExtra("file", if (test) null as String? else doc?.file?.path).putExtra("paper", s.paper.name)
            .putExtra("copies", s.copies).putExtra("gray", s.grayscale).putExtra("landscape", s.landscape)
        return try { ContextCompat.startForegroundService(app, intent); id }
        catch (e: Exception) { error.value = "Printing couldn't start. Keep Printy open and try again."; null }
    }
}
