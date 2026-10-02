// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.printing

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/** A user-initiated Android allowance; never changes battery settings silently. */
object PrintPower {
    fun backgroundAllowed(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun openSettings(context: Context) {
        val packageUri = "package:${context.packageName}".toUri()
        val request = if (backgroundAllowed(context)) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            else Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        try { context.startActivity(request) }
        catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
        }
    }

    fun diagnostics(context: Context): String {
        val power = context.getSystemService(PowerManager::class.java)
        return "Screen interactive: ${power.isInteractive}; device idle: ${power.isDeviceIdleMode}; " +
            "battery optimization exempt: ${power.isIgnoringBatteryOptimizations(context.packageName)}"
    }
}
