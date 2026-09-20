// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app

import org.junit.Assert.*
import org.junit.Test
import org.printy.app.printing.*

class PrintPlanTest {
    @Test fun defaultsPrintEachPageOnceInOrder() {
        val plan = PrintPlan.create(3, PrintSettings())
        assertEquals(listOf(0, 1, 2), plan.selectedPages)
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), plan.sheets.map { it.pages })
    }
    @Test fun customRangesAcceptSpacesAndEnDashesAndRemoveOverlaps() {
        assertEquals(listOf(0, 2, 3, 4, 7), PrintPlan.parseRange("8, 3–5, 1, 4, 3 - 4", 8))
    }
    @Test fun invalidRangesExplainHowToFixThem() {
        listOf("", "0", "9", "5-2", "1,", ",1", "1,,3", "one", "1.5", "-1", "1-", "1-2-3", "99999999999999999999").forEach {
            val error = assertThrows(IllegalArgumentException::class.java) { PrintPlan.parseRange(it, 8) }
            assertFalse(error.message.isNullOrBlank())
        }
    }
    @Test fun overlyLongRangesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PrintPlan.parseRange("1,".repeat(1001), 8) }
    }
    @Test fun oddAndEvenReferToHumanPageNumbers() {
        assertEquals(listOf(0, 2, 4), PrintPlan.create(5, PrintSettings(selection = PageSelection.ODD)).selectedPages)
        assertEquals(listOf(1, 3), PrintPlan.create(5, PrintSettings(selection = PageSelection.EVEN)).selectedPages)
    }
    @Test fun emptySelectionCannotBecomeAnEmptyPrintJob() {
        assertThrows(IllegalArgumentException::class.java) { PrintPlan.create(1, PrintSettings(selection = PageSelection.EVEN)) }
        assertThrows(IllegalArgumentException::class.java) { PrintPlan.create(0, PrintSettings()) }
    }
    @Test fun twoPagesAreGroupedAfterFilteringWithABlankLastSlot() {
        val plan = PrintPlan.create(8, PrintSettings(selection = PageSelection.CUSTOM, pageRange = "2, 5-6", layout = SheetLayout.TWO))
        assertEquals(listOf(listOf(1, 4), listOf(5, null)), plan.sheets.map { it.pages })
    }
    @Test fun fourPagesAreInReadingOrderWithBlankPadding() {
        val plan = PrintPlan.create(5, PrintSettings(layout = SheetLayout.FOUR))
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(4, null, null, null)), plan.sheets.map { it.pages })
    }
    @Test fun reverseIsAppliedBeforeGrouping() {
        val plan = PrintPlan.create(7, PrintSettings(selection = PageSelection.ODD, reverse = true, layout = SheetLayout.TWO))
        assertEquals(listOf(listOf(6, 4), listOf(2, 0)), plan.sheets.map { it.pages })
    }
    @Test fun copiesRepeatThePlanWithoutChangingItsPageGrouping() {
        val once = PrintPlan.create(5, PrintSettings(layout = SheetLayout.TWO))
        val twice = PrintPlan.create(5, PrintSettings(layout = SheetLayout.TWO, copies = 2))
        assertEquals(once, twice)
        assertEquals(6, twice.sheets.size * 2)
    }
    @Test fun nativeSpoolerPagesKeepTheirOwnLayout() {
        assertEquals(listOf(0, 1), PrintPlan.create(2, PrintSettings(systemLayout = true)).selectedPages)
        assertThrows(IllegalArgumentException::class.java) { PrintSettings(systemLayout = true, layout = SheetLayout.TWO) }
        assertThrows(IllegalArgumentException::class.java) { PrintSettings(systemLayout = true, selection = PageSelection.ODD) }
        assertThrows(IllegalArgumentException::class.java) { PrintSettings(systemLayout = true, reverse = true) }
    }
}
