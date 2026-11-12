package it.miaterapia.family

import it.miaterapia.family.data.DoseStatus
import it.miaterapia.family.data.StockUnit
import it.miaterapia.family.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.*

class TherapyRulesTest {
    @Test fun t01ThirtyActiveMedicationsIsTheLimit() {
        assertTrue(TherapyLimits.canActivateMedication(29))
        assertFalse(TherapyLimits.canActivateMedication(30))
    }

    @Test fun t02HalfTabletUsesExactDecimal() {
        val result = InventoryRules.consume(StockEntry(BigDecimal("10"), StockUnit.TABLET), StockEntry(BigDecimal("0.5"), StockUnit.TABLET))
        assertEquals(BigDecimal("9.5"), result.amount)
    }

    @Test fun t03SyrupUsesMillilitres() {
        val result = InventoryRules.consume(StockEntry(BigDecimal("150"), StockUnit.MILLILITER), StockEntry(BigDecimal("10"), StockUnit.MILLILITER))
        assertEquals(BigDecimal("140"), result.amount)
    }

    @Test fun t04PenCountsDoses() {
        val result = InventoryRules.consume(StockEntry(BigDecimal("4"), StockUnit.DOSE), StockEntry(BigDecimal.ONE, StockUnit.DOSE))
        assertEquals(BigDecimal("3"), result.amount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun t05DropsNeverConvertWithoutDocumentedRatio() {
        InventoryRules.convert(BigDecimal("4"), StockUnit.DROP, StockUnit.MILLILITER, null)
    }

    @Test fun maximumFiveDailyTimes() {
        ScheduleRule(LocalDate.now(), null, (8..12).map { LocalTime.of(it, 0) }, zoneId = ZoneId.of("Europe/Rome"), doseAmount = BigDecimal.ONE, doseUnit = StockUnit.TABLET)
    }

    @Test(expected = IllegalArgumentException::class)
    fun sixthDailyTimeIsRejected() {
        ScheduleRule(LocalDate.now(), null, (8..13).map { LocalTime.of(it, 0) }, zoneId = ZoneId.of("Europe/Rome"), doseAmount = BigDecimal.ONE, doseUnit = StockUnit.TABLET)
    }

    @Test fun scheduleUsesStableIdsAndWindow() {
        val start = LocalDate.of(2026, 11, 1)
        val rule = ScheduleRule(start, null, listOf(LocalTime.of(8, 0)), setOf(DayOfWeek.MONDAY), ZoneId.of("Europe/Rome"), BigDecimal.ONE, StockUnit.TABLET)
        val first = ScheduleEngine.generate(rule, start, 14)
        val second = ScheduleEngine.generate(rule, start, 14)
        assertEquals(2, first.size)
        assertEquals(first.map { it.occurrenceId }, second.map { it.occurrenceId })
    }

    @Test fun daylightSavingTransitionDoesNotDuplicateOccurrence() {
        val date = LocalDate.of(2026, 10, 25)
        val rule = ScheduleRule(date, date, listOf(LocalTime.of(2, 30)), zoneId = ZoneId.of("Europe/Rome"), doseAmount = BigDecimal.ONE, doseUnit = StockUnit.TABLET)
        assertEquals(1, ScheduleEngine.generate(rule, date, 1).size)
    }

    @Test fun separateSchedulesNeverShareOccurrenceIds() {
        val date = LocalDate.of(2026, 11, 12)
        val common = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
        val first = ScheduleRule(date, null, common, zoneId = ZoneId.of("Europe/Rome"), doseAmount = BigDecimal.ONE, doseUnit = StockUnit.TABLET, scheduleId = "plan-a")
        val second = first.copy(scheduleId = "plan-b")
        val firstIds = ScheduleEngine.generate(first, date, 14).map { it.occurrenceId }.toSet()
        val secondIds = ScheduleEngine.generate(second, date, 14).map { it.occurrenceId }.toSet()
        assertTrue(firstIds.intersect(secondIds).isEmpty())
    }

    @Test fun stateMachineAllowsRepeatedSnooze() {
        val machine = DoseStateMachine()
        machine.notified(); machine.snoozed(); machine.notified(); machine.snoozed(); machine.taken()
        assertEquals(DoseStatus.TAKEN, machine.state)
    }

    @Test(expected = IllegalStateException::class)
    fun takenDoseCannotBeTakenAgain() {
        val machine = DoseStateMachine(); machine.taken(); machine.taken()
    }
}
