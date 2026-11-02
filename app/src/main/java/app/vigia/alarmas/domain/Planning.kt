package app.vigia.alarmas.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.serialization.Serializable

data class Bedtime(val time: ZonedDateTime, val cycles: Int, val sleepMinutes: Int)

object SleepPlanner {
    const val CYCLE_MINUTES = 90
    const val FALL_ASLEEP_MINUTES = 15

    fun bedtimes(wake: ZonedDateTime): List<Bedtime> = listOf(6, 5, 4).map { cycles ->
        val minutes = cycles * CYCLE_MINUTES
        Bedtime(wake.minusMinutes((minutes + FALL_ASLEEP_MINUTES).toLong()), cycles, minutes)
    }
}

data class WakeRecord(val date: LocalDate, val time: LocalTime, val snoozes: Int)

data class SleepStats(
    val records: List<WakeRecord>,
    val averageWake: LocalTime?,
    val averageSnoozes: Double,
    val skipped: Int,
)

data class HistoryPoint(val date: LocalDate, val time: LocalTime, val type: String)

object SleepAnalytics {
    const val DISMISSED = "Apagada"
    const val SNOOZED = "Pospuesta"
    const val SKIPPED = "Saltada"

    fun build(points: List<HistoryPoint>, today: LocalDate, days: Long = 14): SleepStats {
        val first = today.minusDays(days - 1)
        val window = points.filter { !it.date.isBefore(first) && !it.date.isAfter(today) }
        val records = window.groupBy { it.date }
            .mapNotNull { (date, entries) ->
                val last = entries.filter { it.type == DISMISSED }.maxByOrNull { it.time }
                    ?: return@mapNotNull null
                WakeRecord(date, last.time, entries.count { it.type == SNOOZED })
            }
            .sortedBy { it.date }
        val average = if (records.isEmpty()) {
            null
        } else {
            LocalTime.ofSecondOfDay(records.map { it.time.toSecondOfDay().toLong() }.average().toLong())
        }
        return SleepStats(
            records = records,
            averageWake = average,
            averageSnoozes = records.map { it.snoozes }.average().takeUnless { it.isNaN() } ?: 0.0,
            skipped = window.count { it.type == SKIPPED },
        )
    }
}

@Serializable
data class TimerStep(val name: String = "", val seconds: Int = 300)

/** Avance de un temporizador encadenado. */
object TimerPlan {
    data class Position(val step: Int, val loop: Int)

    fun next(position: Position, stepCount: Int, loops: Int): Position? {
        if (position.step + 1 < stepCount) return position.copy(step = position.step + 1)
        if (position.loop + 1 < loops) return Position(0, position.loop + 1)
        return null
    }
}

object Durations {
    fun clock(millis: Long, withHundredths: Boolean = false): String {
        val total = Duration.ofMillis(millis.coerceAtLeast(0))
        val hours = total.toHours()
        val minutes = total.toMinutesPart()
        val seconds = total.toSecondsPart()
        val base = if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
        return if (withHundredths) "$base,%02d".format(total.toMillisPart() / 10) else base
    }

    fun human(duration: Duration): String {
        val days = duration.toDays()
        val hours = duration.toHoursPart()
        val minutes = duration.toMinutesPart()
        return when {
            days > 0 -> "$days d $hours h"
            hours > 0 -> "$hours h $minutes min"
            else -> "${maxOf(minutes, 1)} min"
        }
    }
}
