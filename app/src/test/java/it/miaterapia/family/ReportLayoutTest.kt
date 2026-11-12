package it.miaterapia.family

import it.miaterapia.family.reports.ReportLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportLayoutTest {
    @Test fun t25ThirtyOneDaysSplitAcrossTwoPages() {
        assertEquals(listOf(1..15, 16..31), ReportLayout.dayRanges(31))
    }

    @Test fun shortFebruaryStillUsesTwoReadableHalves() {
        assertEquals(listOf(1..15, 16..28), ReportLayout.dayRanges(28))
    }
}
