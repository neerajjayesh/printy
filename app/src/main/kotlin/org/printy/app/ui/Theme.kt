// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable fun PrintyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF9CD3AF), secondary = Color(0xFFBACBBC), background = Color(0xFF101512))
        else -> lightColorScheme(primary = Color(0xFF386A51), secondary = Color(0xFF506354), background = Color(0xFFF7FAF5), surface = Color(0xFFF7FAF5))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
