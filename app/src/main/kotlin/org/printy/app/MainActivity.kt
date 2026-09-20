// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import org.printy.app.ui.PrintyTheme
import org.printy.app.ui.PrintyUi
import org.printy.app.ui.PrintyViewModel

class MainActivity : ComponentActivity() {
    private lateinit var model: PrintyViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[PrintyViewModel::class.java]
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
