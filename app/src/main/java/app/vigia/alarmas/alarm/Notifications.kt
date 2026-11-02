package app.vigia.alarmas.alarm

import app.vigia.alarmas.domain.tr
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.vigia.alarmas.R
import app.vigia.alarmas.clock.ClockActions
import app.vigia.alarmas.domain.Durations
import app.vigia.alarmas.ui.MainActivity
import app.vigia.alarmas.ui.RingingActivity
import java.time.LocalDate

object Notifications {
    private const val CHANNEL_ALARM = "alarm_ringing"
    private const val CHANNEL_CHECK = "alarm_checking"
    private const val CHANNEL_NOTICE = "evening_notice"
    private const val CHANNEL_STOPWATCH = "stopwatch"
    private const val CHANNEL_TIMERS = "timers"
    private const val CHANNEL_STEP = "timer_step"

    const val SERVICE_ID = 1
    const val RINGING_ID = 3
    private const val CALENDAR_WARNING_ID = 2
    private const val STOPWATCH_ID = 4
    private const val TIMER_STEP_ID = 5
    private const val NOTICE_BASE_ID = 1000
    private const val TIMER_NOTIFICATION_BASE = 5000

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, tr("Alarma sonando", "Alarm ringing"), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_CHECK, tr("Comprobación de alarmas", "Alarm checks"), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_NOTICE, tr("Avisos de la noche anterior", "Night-before notices"), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STOPWATCH, tr("Cronómetro", "Stopwatch"), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMERS, tr("Temporizadores en marcha", "Running timers"), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STEP, tr("Cambio de tramo", "Timer step change"), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun checking(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_CHECK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Comprobando alarma", "Checking alarm"))
            .setOngoing(true)
            .build()

    fun ringing(context: Context, state: RingState): Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, RingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(state.label)
            .setContentText(tr("Alarma sonando", "Alarm ringing"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
        if (state.snoozeMinutes != null) {
            builder.addAction(0, tr("Posponer", "Snooze"), serviceAction(context, AlarmService.ACTION_SNOOZE, 1))
        }
        if (state.burst) {
            builder.addAction(0, tr("Detener", "Stop"), serviceAction(context, AlarmService.ACTION_DISMISS, 2))
            if (!AlarmService.needsChallenge(state)) {
                builder.addAction(
                    0,
                    tr("Detener por hoy", "Stop for today"),
                    serviceAction(context, AlarmService.ACTION_STOP_DAY, 3),
                )
            }
        } else if (!AlarmService.needsChallenge(state)) {
            builder.addAction(0, tr("Apagar", "Dismiss"), serviceAction(context, AlarmService.ACTION_DISMISS, 2))
        }
        return builder.build()
    }

    fun stopwatch(context: Context, startedAt: Long?, elapsedMs: Long): Notification {
        val running = startedAt != null
        val builder = NotificationCompat.Builder(context, CHANNEL_STOPWATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (running) tr("Cronómetro en marcha", "Stopwatch running") else tr("Cronómetro en pausa", "Stopwatch paused"))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
        if (running) {
            builder.setUsesChronometer(true).setWhen(System.currentTimeMillis() - elapsedMs).setShowWhen(true)
            builder.addAction(0, tr("Vuelta", "Lap"), clockAction(context, ClockActions.LAP, 11))
            builder.addAction(0, tr("Pausar", "Pause"), clockAction(context, ClockActions.PAUSE, 12))
        } else {
            builder.setContentText(Durations.clock(elapsedMs))
            builder.addAction(0, tr("Reanudar", "Resume"), clockAction(context, ClockActions.RESUME, 13))
            builder.addAction(0, tr("Reiniciar", "Reset"), clockAction(context, ClockActions.RESET, 14))
        }
        return builder.build()
    }

    fun postStopwatch(context: Context, startedAt: Long?, elapsedMs: Long) {
        notify(context, STOPWATCH_ID, stopwatch(context, startedAt, elapsedMs))
    }

    fun cancelStopwatch(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(STOPWATCH_ID)
    }

    fun postTimer(context: Context, timerId: Long, title: String, endAt: Long) {
        val notification = NotificationCompat.Builder(context, CHANNEL_TIMERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAt)
            .setShowWhen(true)
            .addAction(
                0,
                tr("Pausar", "Pause"),
                PendingIntent.getBroadcast(
                    context,
                    TIMER_NOTIFICATION_BASE + timerId.toInt(),
                    Intent(context, ActionReceiver::class.java)
                        .setAction(ClockActions.TIMER_PAUSE)
                        .putExtra(ActionReceiver.EXTRA_ALARM_ID, timerId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        notify(context, TIMER_NOTIFICATION_BASE + timerId.toInt(), notification)
    }

    fun cancelTimer(context: Context, timerId: Long) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(TIMER_NOTIFICATION_BASE + timerId.toInt())
    }

    fun timerStep(context: Context, title: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_STEP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notify(context, TIMER_STEP_ID, notification)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).notify(id, notification)
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        50,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun clockAction(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, ActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun skippedTomorrow(context: Context, skipped: SkippedTomorrow) {
        val alarm = skipped.alarm
        val force = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            Intent(context, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_FORCE)
                .putExtra(ActionReceiver.EXTRA_ALARM_ID, alarm.id)
                .putExtra(ActionReceiver.EXTRA_DATE, skipped.date.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val config = alarm.config
        val time = "%02d:%02d".format(config.hour, config.minute)
        val notification = NotificationCompat.Builder(context, CHANNEL_NOTICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("Mañana no sonará la alarma de las $time", "The $time alarm won't ring tomorrow"))
            .setContentText(tr("Motivo: ${skipped.reason}", "Reason: ${skipped.reason}"))
            .setAutoCancel(true)
            .addAction(0, tr("Sonar igualmente", "Ring anyway"), force)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(noticeId(alarm.id), notification)
    }

    fun calendarUnavailable(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_NOTICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tr("No se pudo leer el calendario", "Couldn't read the calendar"))
            .setContentText(tr("Las alarmas con exclusiones de calendario sonarán.", "Alarms with calendar exclusions will ring."))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(CALENDAR_WARNING_ID, notification)
    }

    fun cancelChecking(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(SERVICE_ID)
    }

    fun cancelNotice(context: Context, alarmId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(noticeId(alarmId))
    }

    private fun noticeId(alarmId: Long): Int = NOTICE_BASE_ID + alarmId.toInt()

    private fun serviceAction(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getService(
            context,
            request,
            Intent(context, AlarmService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
