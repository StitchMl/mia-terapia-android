package it.miaterapia.family.domain

import it.miaterapia.family.data.DoseStatus
import it.miaterapia.family.data.StockUnit
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** Regola immutabile usata per materializzare le singole assunzioni di una terapia. */
data class ScheduleRule(
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val times: List<LocalTime>,
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val zoneId: ZoneId,
    val doseAmount: BigDecimal,
    val doseUnit: StockUnit,
    val scheduleId: String = "default"
) {
    init {
        require(times.isNotEmpty()) { "Serve almeno un orario" }
        require(times.size <= MAX_DAILY_TIMES) { "Sono consentiti al massimo cinque orari al giorno" }
        require(times.distinct().size == times.size) { "Gli orari non possono essere duplicati" }
        require(doseAmount > BigDecimal.ZERO) { "La quantità deve essere positiva" }
        require(endDate == null || !endDate.isBefore(startDate)) { "La data finale precede quella iniziale" }
    }

    companion object { const val MAX_DAILY_TIMES = 5 }
}

data class PlannedOccurrence(
    val occurrenceId: String,
    val scheduledAt: ZonedDateTime,
    val doseAmount: BigDecimal,
    val doseUnit: StockUnit,
    val status: DoseStatus = DoseStatus.PLANNED
)

/**
 * Genera occorrenze deterministiche in una finestra limitata.
 *
 * Gli ID derivano da piano, data, ora e fuso: rigenerare la stessa finestra non
 * crea duplicati e due terapie con lo stesso orario restano isolate.
 */
object ScheduleEngine {
    fun generate(rule: ScheduleRule, windowStart: LocalDate, windowDays: Long = 14): List<PlannedOccurrence> {
        require(windowDays in 1..31) { "La finestra deve essere compresa tra 1 e 31 giorni" }
        val from = maxOf(windowStart, rule.startDate)
        val until = minOf(from.plusDays(windowDays - 1), rule.endDate ?: LocalDate.MAX)
        if (until.isBefore(from)) return emptyList()
        val results = mutableListOf<PlannedOccurrence>()
        var date = from
        while (!date.isAfter(until)) {
            if (date.dayOfWeek in rule.daysOfWeek) rule.times.sorted().forEach { time ->
                val scheduled = ZonedDateTime.of(date, time, rule.zoneId)
                results += PlannedOccurrence(
                    occurrenceId = stableOccurrenceId(rule.scheduleId, date, time, rule.zoneId),
                    scheduledAt = scheduled,
                    doseAmount = rule.doseAmount,
                    doseUnit = rule.doseUnit
                )
            }
            date = date.plusDays(1)
        }
        return results
    }

    private fun stableOccurrenceId(scheduleId: String, date: LocalDate, time: LocalTime, zone: ZoneId): String =
        UUID.nameUUIDFromBytes("$scheduleId|$date|$time|${zone.id}".toByteArray()).toString()
}

data class StockEntry(val amount: BigDecimal, val unit: StockUnit)

/** Operazioni pure sulle scorte; [BigDecimal] evita errori sulle dosi frazionarie. */
object InventoryRules {
    fun apply(balance: StockEntry, movement: StockEntry): StockEntry {
        require(balance.unit == movement.unit) { "Unità di scorta incompatibili" }
        return balance.copy(amount = balance.amount.add(movement.amount))
    }

    fun consume(balance: StockEntry, dose: StockEntry): StockEntry = apply(balance, dose.copy(amount = dose.amount.negate()))

    fun convert(value: BigDecimal, from: StockUnit, to: StockUnit, documentedRatio: BigDecimal?): BigDecimal {
        if (from == to) return value
        require(documentedRatio != null && documentedRatio > BigDecimal.ZERO) { "Conversione non documentata" }
        return value.multiply(documentedRatio)
    }
}

object TherapyLimits {
    const val MAX_ACTIVE_MEDICATIONS_PER_PATIENT = 30
    fun canActivateMedication(activeCount: Int) = activeCount < MAX_ACTIVE_MEDICATIONS_PER_PATIENT
}

/** Impedisce transizioni clinicamente incoerenti, come confermare due volte una dose. */
class DoseStateMachine(initial: DoseStatus = DoseStatus.PLANNED) {
    var state: DoseStatus = initial
        private set

    fun notified() = transition(setOf(DoseStatus.PLANNED, DoseStatus.SNOOZED), DoseStatus.NOTIFIED)
    fun snoozed() = transition(setOf(DoseStatus.NOTIFIED), DoseStatus.SNOOZED)
    fun taken() = transition(setOf(DoseStatus.PLANNED, DoseStatus.NOTIFIED, DoseStatus.SNOOZED), DoseStatus.TAKEN)
    fun skipped() = transition(setOf(DoseStatus.PLANNED, DoseStatus.NOTIFIED, DoseStatus.SNOOZED), DoseStatus.SKIPPED)
    fun unconfirmed() = transition(setOf(DoseStatus.PLANNED, DoseStatus.NOTIFIED, DoseStatus.SNOOZED), DoseStatus.UNCONFIRMED)
    fun cancelled() = transition(setOf(DoseStatus.PLANNED), DoseStatus.CANCELLED)

    private fun transition(allowed: Set<DoseStatus>, target: DoseStatus) {
        check(state in allowed) { "Transizione non valida: $state -> $target" }
        state = target
    }
}
