package app.vigia.alarmas.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.vigia.alarmas.clock.TimerReceiver
import app.vigia.alarmas.ui.MainActivity
import java.time.LocalDate

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun schedule(alarmId: Long, atMillis: Long, date: LocalDate?, snoozed: Boolean, backup: Boolean = false) {
        val fire = PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            fireIntent(alarmId)
                .putExtra(EXTRA_DATE, date?.toString())
                .putExtra(EXTRA_SNOOZED, snoozed)
                .putExtra(EXTRA_BACKUP, backup),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context,
            requestCode(alarmId),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), fire)
    }

    fun cancel(alarmId: Long) {
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(alarmId),
            fireIntent(alarmId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    fun scheduleNotice(atMillis: Long) {
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, noticeIntent())
    }

    fun scheduleTimer(timerId: Long, atMillis: Long) {
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, timerIntent(timerId))
    }

    fun cancelTimer(timerId: Long) {
        manager.cancel(timerIntent(timerId))
    }

    fun cancelNotice() {
        manager.cancel(noticeIntent())
    }

    fun nextSystemAlarm(): Long? = manager.nextAlarmClock?.triggerTime

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun timerIntent(timerId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        TIMER_REQUEST_BASE + timerId.toInt(),
        Intent(context, TimerReceiver::class.java).putExtra(EXTRA_TIMER_ID, timerId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun noticeIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        NOTICE_REQUEST,
        Intent(context, NoticeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun fireIntent(alarmId: Long): Intent =
        Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("vigia://alarm/$alarmId"))
            .putExtra(EXTRA_ALARM_ID, alarmId)

    private fun requestCode(alarmId: Long): Int = (alarmId % Int.MAX_VALUE).toInt()

    companion object {
        const val ACTION_FIRE = "app.vigia.alarmas.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_DATE = "date"
        const val EXTRA_SNOOZED = "snoozed"
        const val EXTRA_BACKUP = "backup"
        const val EXTRA_TIMER_ID = "timer_id"
        const val TEST_ALARM_ID = -1L
        private const val TIMER_REQUEST_BASE = 1_000_000
        private const val NOTICE_REQUEST = 900_000
    }
}
