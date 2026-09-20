// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.printing

import org.printy.escp.Paper

enum class PageSelection(val label: String) {
    ALL("All pages"), CUSTOM("Choose pages"), ODD("Odd pages"), EVEN("Even pages")
}

enum class SheetLayout(val label: String, val columns: Int, val rows: Int) {
    ONE("1 page per sheet", 1, 1), TWO("2 pages side by side", 2, 1), FOUR("4 pages per sheet", 2, 2);
    val capacity get() = columns * rows
}

data class PrintSettings(val paper: Paper = Paper.A4, val copies: Int = 1,
    val grayscale: Boolean = false, val landscape: Boolean = false, val systemLayout: Boolean = false,
    val selection: PageSelection = PageSelection.ALL, val pageRange: String = "",
    val layout: SheetLayout = SheetLayout.ONE, val reverse: Boolean = false) {
    init {
        require(copies in 1..99)
        // The system spooler has already selected and laid out its pages.
        require(!systemLayout || (selection == PageSelection.ALL && layout == SheetLayout.ONE && !reverse))
    }
}

/** Zero-based source indices; null slots keep the unused end of a sheet white. */
data class OutputSheet(val pages: List<Int?>) {
    val description: String get() = pages.joinToString(" · ") { it?.let { "Page ${it + 1}" } ?: "Blank" }
}

data class PrintPlan(val selectedPages: List<Int>, val sheets: List<OutputSheet>) {
    companion object {
        fun create(pageCount: Int, settings: PrintSettings): PrintPlan {
            require(pageCount > 0) { "This document has no pages to print." }
            val selected = when (settings.selection) {
                PageSelection.ALL -> (0 until pageCount).toList()
                PageSelection.ODD -> (0 until pageCount step 2).toList()
                PageSelection.EVEN -> (1 until pageCount step 2).toList()
                PageSelection.CUSTOM -> parseRange(settings.pageRange, pageCount)
            }.let { if (settings.reverse) it.reversed() else it }
            require(selected.isNotEmpty()) { "There are no ${settings.selection.label.lowercase()} in this document. Choose another page selection." }
            val sheets = selected.chunked(settings.layout.capacity).map { chunk ->
                OutputSheet(List(settings.layout.capacity) { chunk.getOrNull(it) })
            }
            return PrintPlan(selected, sheets)
        }

        /** Ranges select pages in document order; overlaps do not print duplicate pages. */
        fun parseRange(text: String, pageCount: Int): List<Int> {
            require(text.isNotBlank()) { "Enter pages such as 1, 3–5." }
            require(text.length <= 2000) { "This page list is too long. Use ranges such as 1–20." }
            val selected = sortedSetOf<Int>()
            val itemPattern = Regex("([0-9]+)(?:\\s*[-–]\\s*([0-9]+))?")
            for (part in text.split(',')) {
                val match = itemPattern.matchEntire(part.trim())
                    ?: throw IllegalArgumentException("Use page numbers and ranges, such as 1, 3–5.")
                val first = match.groupValues[1].toIntOrNull()
                val last = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull()
                require(first != null && last != null && first in 1..pageCount && last in 1..pageCount) {
                    "Choose page numbers from 1 to $pageCount."
                }
                require(first <= last) { "Write ranges from low to high, such as 3–5. Use Reverse order to print backward." }
                for (page in first..last) selected.add(page - 1)
            }
            return selected.toList()
        }
    }
}
