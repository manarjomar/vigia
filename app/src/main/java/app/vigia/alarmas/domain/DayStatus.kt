package app.vigia.alarmas.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

enum class DayState { RINGS, SKIPPED, DEPENDS, NO_DATA, NONE }

data class DayStatus(
    val date: LocalDate,
    val state: DayState,
    val time: LocalTime? = null,
    val detail: String? = null,
    val alarmId: Long? = null,
)

data class StatusItem(val spec: AlarmSpec, val label: String, val calendar: CalendarSnapshot)

/** Resume qué hará cada día. Usa las mismas decisiones que la alarma al sonar. */
object DayPlanner {
    fun status(
        items: List<StatusItem>,
        date: LocalDate,
        now: ZonedDateTime,
        holidays: HolidayLookup = OfflineHolidays,
    ): DayStatus {
        val isToday = date == now.toLocalDate()
        val rings = mutableListOf<DayStatus>()
        val skips = mutableListOf<DayStatus>()

        items.filter { it.spec.enabled }.forEach { item ->
            val spec = item.spec
            if (!spec.config.runsOn(date)) return@forEach
            if (spec.config.isOneShot) {
                val plan = AlarmEngine.plan(spec, now, item.calendar, holidays)
                val planned = plan.fireAt?.toLocalDate() == date || plan.skipped.any { it.date == date }
                if (!planned) return@forEach
            }

            when (val decision = AlarmEngine.evaluate(spec, date, item.calendar, zone = now.zone, holidays = holidays)) {
                is Decision.Ring -> {
                    val time = AlarmEngine.occurrences(decision)
                        .firstOrNull { !isToday || it.isAfter(now.toLocalTime()) }
                        ?: return@forEach
                    rings += DayStatus(date, ringState(spec, decision), time, decision.note ?: item.label, spec.id)
                }
                is Decision.Skip -> {
                    val base = LocalTime.of(spec.config.hour, spec.config.minute)
                    if (!isToday || base.isAfter(now.toLocalTime())) {
                        skips += DayStatus(date, DayState.SKIPPED, base, decision.reason, spec.id)
                    }
                }
            }
        }

        return rings.minByOrNull { it.time ?: LocalTime.MAX }
            ?: skips.minByOrNull { it.time ?: LocalTime.MAX }
            ?: DayStatus(date, DayState.NONE)
    }

    /** Hoy, si aún queda algo por sonar; si no, mañana. */
    fun focus(
        items: List<StatusItem>,
        now: ZonedDateTime,
        holidays: HolidayLookup = OfflineHolidays,
    ): DayStatus {
        val today = status(items, now.toLocalDate(), now, holidays)
        return if (today.state != DayState.NONE) today else status(items, now.toLocalDate().plusDays(1), now, holidays)
    }

    fun week(
        items: List<StatusItem>,
        now: ZonedDateTime,
        holidays: HolidayLookup = OfflineHolidays,
    ): List<DayStatus> = (0L until 7L).map { status(items, now.toLocalDate().plusDays(it), now, holidays) }

    private fun ringState(spec: AlarmSpec, decision: Decision.Ring): DayState {
        val warnings = setOf(AlarmEngine.CALENDAR_WARNING, AlarmEngine.HOLIDAY_NOTE)
        val dependsOnContext = spec.config.rules.any { rule -> rule.conditions.any { it is ContextCondition } }
        return when {
            decision.note in warnings -> DayState.NO_DATA
            dependsOnContext -> DayState.DEPENDS
            else -> DayState.RINGS
        }
    }
}
