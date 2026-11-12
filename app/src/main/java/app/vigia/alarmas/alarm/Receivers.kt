package app.vigia.alarmas.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.vigia.alarmas.Graph
import app.vigia.alarmas.clock.ClockActions
import app.vigia.alarmas.clock.Stopwatch
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_FIRE)
            .putExtras(intent)
        ContextCompat.startForegroundService(context, service)
    }
}

class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val allowed = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
        if (intent.action !in allowed) return
        runAsync {
            Graph.coordinator(context).reconcile()
            Graph.timers(context).restore()
        }
    }
}

class NoticeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync {
            val coordinator = Graph.coordinator(context)
            val report = coordinator.eveningReport()
            report.skipped.forEach { Notifications.skippedTomorrow(context, it) }
            report.changed.forEach { Notifications.changedTomorrow(context, it) }
            if (report.calendarUnavailable) Notifications.calendarUnavailable(context)
            coordinator.reconcile()
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stopwatch = Stopwatch.get(context)
        when (intent.action) {
            ACTION_FORCE -> forceAlarm(context, intent)
            ACTION_SKIP -> skipAlarm(context, intent)
            ACTION_REFRESH -> runAsync { Graph.coordinator(context).refreshWidgets() }
            ClockActions.LAP -> stopwatch.lap()
            ClockActions.PAUSE -> stopwatch.pause()
            ClockActions.RESUME -> stopwatch.start()
            ClockActions.RESET -> stopwatch.reset()
            ClockActions.TIMER_PAUSE -> {
                val id = intent.getLongExtra(EXTRA_ALARM_ID, 0)
                runAsync { Graph.timers(context).pause(id) }
            }
        }
    }

    private fun skipAlarm(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0)
        val date = intent.getStringExtra(EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return
        runAsync { Graph.coordinator(context).skipDate(alarmId, date) }
    }

    private fun forceAlarm(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0)
        val date = intent.getStringExtra(EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return
        runAsync {
            Graph.coordinator(context).forceDate(alarmId, date)
            Notifications.cancelNotice(context, alarmId)
        }
    }

    companion object {
        const val ACTION_FORCE = "app.vigia.alarmas.FORCE"
        const val ACTION_SKIP = "app.vigia.alarmas.SKIP"
        const val ACTION_REFRESH = "app.vigia.alarmas.REFRESH"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_DATE = "date"
    }
}
