// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.printing

import android.app.*
import android.content.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import org.printy.app.MainActivity
import org.printy.app.PrintyApplication
import org.printy.app.R

class JobNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init { manager.createNotificationChannel(NotificationChannel("printing", "Print progress", NotificationManager.IMPORTANCE_LOW)) }
    fun build(state: JobState): Notification {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, "printing")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(state.title).setContentText(state.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(state.message))
            .setContentIntent(open).setOnlyAlertOnce(true).setAutoCancel(!state.active)
            .apply {
                if (state.active) {
                    setProgress(100, (state.progress * 100).toInt(), state.total == 0)
                    val cancel = Intent(context, CancelReceiver::class.java).putExtra("job", state.id)
                        .setData("printy://cancel/${state.id}".toUri())
                    addAction(0, "Cancel", PendingIntent.getBroadcast(context, 0, cancel, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                }
            }.build()
    }
    fun show(state: JobState) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        runCatching { manager.notify(state.id, 1, build(state)) }
    }
    fun dismiss(id: String) = manager.cancel(id, 1)
}
class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("job")?.let { (context.applicationContext as PrintyApplication).jobs.cancel(it) }
    }
}
