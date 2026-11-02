package app.vigia.alarmas.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed interface ShiftSlot {
    data class Work(val type: ShiftType) : ShiftSlot
    data object Rest : ShiftSlot
    data object Unassigned : ShiftSlot
}

object ShiftPlanner {
    /** Orden de prioridad: día marcado a mano, evento de calendario y, por último, ciclo. */
    fun slot(plan: ShiftPlan, date: LocalDate, calendar: CalendarSnapshot): ShiftSlot {
        plan.overrides[date.toString()]?.let { return slotFor(plan, it) }

        if (calendar is CalendarSnapshot.Available && plan.calendarIds.isNotEmpty()) {
            val titles = calendar.events
                .filter { it.calendarId in plan.calendarIds && date >= it.firstDate && date <= it.lastDate }
                .map { AlarmEngine.normalize(it.title) }
            plan.types.firstOrNull { type ->
                val name = AlarmEngine.normalize(type.name)
                name.isNotEmpty() && titles.any { it.contains(name) }
            }?.let { return ShiftSlot.Work(it) }
        }

        if (plan.cycle.isEmpty()) return ShiftSlot.Unassigned
        val days = ChronoUnit.DAYS.between(plan.start, date)
        return slotFor(plan, plan.cycle[Math.floorMod(days, plan.cycle.size.toLong()).toInt()])
    }

    private fun slotFor(plan: ShiftPlan, id: String): ShiftSlot =
        plan.types.firstOrNull { it.id == id }?.let { ShiftSlot.Work(it) } ?: ShiftSlot.Rest
}
