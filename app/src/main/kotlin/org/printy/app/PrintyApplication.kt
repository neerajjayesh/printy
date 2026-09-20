// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app

import android.app.Application
import org.printy.app.data.PrinterStore
import org.printy.app.printing.JobController
import java.io.File

class PrintyApplication : Application() {
    lateinit var printers: PrinterStore; private set
    lateinit var jobs: JobController; private set
    override fun onCreate() {
        super.onCreate()
        printers = PrinterStore(this)
        // No job is replayed after process death. Old document copies never leave app storage.
        File(cacheDir, "documents").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
        jobs = JobController(this, printers)
    }
}
