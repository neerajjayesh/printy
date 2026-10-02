// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.printy.app.ui.PrintyTheme
import org.printy.app.ui.PrintyUi
import org.printy.app.ui.PrintyViewModel

class MainActivity : ComponentActivity() {
    private lateinit var model: PrintyViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[PrintyViewModel::class.java]
        // Observe the queue, not job-card visibility. Keep observing while stopped so a
        // completed background job clears the flag before this window returns.
        lifecycleScope.launch {
            try {
                model.jobs.map { jobs -> jobs.any { it.active } }.distinctUntilChanged().collect { printing ->
                    if (printing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            } finally { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        if (savedInstanceState == null) handleShare(intent)
        setContent { PrintyTheme { PrintyUi(model) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleShare(intent) }
    private fun handleShare(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION") val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri?.scheme == "content") model.import(uri)
        }
    }
}
