package app.vigia.alarmas.clock

import app.vigia.alarmas.domain.tr
import android.content.Context
import app.vigia.alarmas.alarm.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ClockActions {
    const val LAP = "app.vigia.alarmas.stopwatch.LAP"
    const val PAUSE = "app.vigia.alarmas.stopwatch.PAUSE"
    const val RESUME = "app.vigia.alarmas.stopwatch.RESUME"
    const val RESET = "app.vigia.alarmas.stopwatch.RESET"
    const val TIMER_PAUSE = "app.vigia.alarmas.timer.PAUSE"
}

data class StopwatchState(
    val startedAt: Long? = null,
    val accumulatedMs: Long = 0,
    val laps: List<Long> = emptyList(),
) {
    val running: Boolean get() = startedAt != null

    fun elapsed(now: Long = System.currentTimeMillis()): Long =
        accumulatedMs + (startedAt?.let { now - it } ?: 0)
}

/** Guarda instantes de reloj, de modo que sigue contando aunque Android cierre la app. */
class Stopwatch private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<StopwatchState> = _state.asStateFlow()

    fun start() {
        val current = _state.value
        if (!current.running) publish(current.copy(startedAt = System.currentTimeMillis()))
    }

    fun pause() {
        val current = _state.value
        if (current.running) publish(current.copy(startedAt = null, accumulatedMs = current.elapsed()))
    }

    fun lap() {
        val current = _state.value
        if (current.running) publish(current.copy(laps = current.laps + current.elapsed()))
    }

    fun reset() {
        publish(StopwatchState())
    }

    fun exportCsv(): String = buildString {
        appendLine(tr("Vuelta;Tiempo de vuelta (s);Tiempo total (s)", "Lap;Lap time (s);Total time (s)"))
        var previous = 0L
        _state.value.laps.forEachIndexed { index, total ->
            appendLine("%d;%.2f;%.2f".format(index + 1, (total - previous) / 1000.0, total / 1000.0))
            previous = total
        }
    }

    private fun publish(state: StopwatchState) {
        _state.value = state
        prefs.edit()
            .putLong("started_at", state.startedAt ?: 0)
            .putLong("accumulated", state.accumulatedMs)
            .putString("laps", state.laps.joinToString(","))
            .apply()
        if (state.running || state.accumulatedMs > 0) {
            Notifications.postStopwatch(context, state.startedAt, state.elapsed())
        } else {
            Notifications.cancelStopwatch(context)
        }
    }

    private fun load() = StopwatchState(
        startedAt = prefs.getLong("started_at", 0).takeIf { it > 0 },
        accumulatedMs = prefs.getLong("accumulated", 0),
        laps = prefs.getString("laps", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() },
    )

    companion object {
        @Volatile
        private var instance: Stopwatch? = null

        fun get(context: Context): Stopwatch = instance ?: synchronized(this) {
            instance ?: Stopwatch(context.applicationContext).also { instance = it }
        }
    }
}
