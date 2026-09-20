// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Three small local icons keep the APK from bundling the complete extended icon catalog.
private fun outline(name: String, draw: PathBuilder.() -> Unit) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
}.build()
private val printer = outline("Printer") {
    moveTo(6f, 8f); verticalLineTo(3f); horizontalLineTo(18f); verticalLineTo(8f)
    moveTo(6f, 17f); horizontalLineTo(3f); verticalLineTo(8f); horizontalLineTo(21f); verticalLineTo(17f); horizontalLineTo(18f)
    moveTo(6f, 14f); horizontalLineTo(18f); verticalLineTo(21f); horizontalLineTo(6f); close()
    moveTo(17f, 11f); horizontalLineTo(18f)
}
private val document = outline("Document") {
    moveTo(5f, 2f); horizontalLineTo(14f); lineTo(20f, 8f); verticalLineTo(22f); horizontalLineTo(5f); close()
    moveTo(14f, 2f); verticalLineTo(8f); horizontalLineTo(20f)
    moveTo(8f, 12f); horizontalLineTo(16f)
    moveTo(8f, 16f); horizontalLineTo(16f)
}
private val minus = outline("Minus") { moveTo(5f, 12f); horizontalLineTo(19f) }
val Icons.Outlined.Print: ImageVector get() = printer
val Icons.Outlined.Description: ImageVector get() = document
val Icons.Outlined.Remove: ImageVector get() = minus
