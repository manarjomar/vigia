package app.vigia.alarmas.alarm

import app.vigia.alarmas.domain.tr
import android.content.Context
import app.vigia.alarmas.clock.ClockWidget
import app.vigia.alarmas.clock.StatusWidget
import app.vigia.alarmas.clock.WidgetData
import app.vigia.alarmas.data.AlarmEntity
import app.vigia.alarmas.data.CalendarRepository
import app.vigia.alarmas.data.HistoryEntry
import app.vigia.alarmas.data.HolidayRepository
import app.vigia.alarmas.data.HistoryType
import app.vigia.alarmas.data.Settings
import app.vigia.alarmas.data.VigiaDatabase
import app.vigia.alarmas.domain.AlarmConfig
import app.vigia.alarmas.domain.AlarmEngine
import app.vigia.alarmas.domain.AlarmSpec
import app.vigia.alarmas.domain.BurstConfig
import app.vigia.alarmas.domain.CalendarSnapshot
import app.vigia.alarmas.domain.ContextCondition
import app.vigia.alarmas.domain.DayPlanner
import app.vigia.alarmas.domain.DayState
import app.vigia.alarmas.domain.DayStatus
import app.vigia.alarmas.domain.Decision
import app.vigia.alarmas.domain.HolidayCondition
import app.vigia.alarmas.domain.ForecastDay
import app.vigia.alarmas.domain.Plan
import app.vigia.alarmas.domain.StatusItem
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SkippedTomorrow(val alarm: AlarmEntity, val date: LocalDate, val reason: String)

data class ChangedTomorrow(val alarm: AlarmEntity, val time: LocalTime)

data class EveningReport(
    val skipped: List<SkippedTomorrow>,
    val calendarUnavailable: Boolean,
    val changed: List<ChangedTomorrow> = emptyList(),
)

data class OneTimeTarget(val date: LocalDate, val usual: LocalTime, val current: LocalTime?)

data class FireResult(
    val ring: Boolean,
    val config: AlarmConfig = AlarmConfig(),
    val backup: Boolean = false,
    val snoozeCount: Int = 0,
    val burst: BurstConfig? = null,
)

class AlarmCoordinator(
    private val database: VigiaDatabase,
    private val calendars: CalendarRepository,
    private val scheduler: AlarmScheduler,
    private val settings: Settings,
    private val deviceState: DeviceStateReader,
    private val holidays: HolidayRepository,
    private val context: Context,
) {
    private val alarms get() = database.alarms()
    private val history get() = database.history()

    suspend fun reconcile() = withContext(Dispatchers.IO) {
        val now = ZonedDateTime.now()
        alarms.all().map { alarm ->
            val expired = alarm.oneTimeMoment?.toLocalDate()?.isBefore(now.toLocalDate()) == true
            if (expired) alarm.copy(oneTime = null).also { alarms.update(it) } else alarm
        }.forEach { schedule(it, now) }
        scheduleNotice(now)
        refreshWidgets()
    }

    /** Mismo cálculo que usa la alarma, para que el widget nunca diga algo distinto. */
    suspend fun widgetData(): WidgetData = withContext(Dispatchers.IO) {
        val now = ZonedDateTime.now()
        val items = alarms.all().map { alarm ->
            val spec = alarm.toSpec()
            StatusItem(spec, spec.config.label, calendars.snapshot(spec.config.calendarIds, now.toLocalDate(), 9))
        }
        WidgetData(DayPlanner.focus(items, now, holidays), DayPlanner.week(items, now, holidays))
    }

    /** Próxima alarma de Vigía. La del sistema podría ser de otra app. */
    suspend fun nextFire(): ZonedDateTime? = withContext(Dispatchers.IO) {
        alarms.all().filter { it.enabled }.mapNotNull { plan(it).fireAt }.minOrNull()
    }

    suspend fun refreshWidgets() = withContext(Dispatchers.IO) {
        runCatching {
            ClockWidget.show(context, nextFire())
            StatusWidget.show(context, widgetData())
        }
        Unit
    }

    suspend fun skipDate(alarmId: Long, date: LocalDate) = withContext(Dispatchers.IO) {
        val alarm = alarms.byId(alarmId) ?: return@withContext
        val updated = alarm.copy(skipDate = date.toString(), forceDate = null)
        alarms.update(updated)
        schedule(updated, ZonedDateTime.now())
        refreshWidgets()
    }

    /** Lo que hace el botón del widget y del panel rápido: saltar si suena, o hacer que suene. */
    suspend fun toggleStatus(status: DayStatus) {
        val alarmId = status.alarmId ?: return
        if (status.state == DayState.SKIPPED) forceDate(alarmId, status.date) else skipDate(alarmId, status.date)
    }

    /** Mantiene al día los festivos de los países que usan las reglas. */
    suspend fun refreshHolidays() = withContext(Dispatchers.IO) {
        alarms.all()
            .flatMap { alarm -> alarm.config.rules.flatMap { it.conditions } }
            .filterIsInstance<HolidayCondition>()
            .map { it.effectiveCountry }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { holidays.ensure(it) }
    }

    suspend fun save(alarm: AlarmEntity): Long = withContext(Dispatchers.IO) {
        val id = if (alarm.id == 0L) {
            alarms.insert(alarm)
        } else {
            alarms.update(alarm)
            alarm.id
        }
        alarms.byId(id)?.let { schedule(it, ZonedDateTime.now()) }
        refreshWidgets()
        id
    }

    suspend fun delete(alarm: AlarmEntity) = withContext(Dispatchers.IO) {
        scheduler.cancel(alarm.id)
        alarms.delete(alarm)
        refreshWidgets()
    }

    suspend fun setGroupEnabled(group: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        alarms.all().filter { it.config.group == group }.forEach { save(it.copy(enabled = enabled)) }
    }

    suspend fun plan(alarm: AlarmEntity): Plan = withContext(Dispatchers.IO) {
        val now = ZonedDateTime.now()
        val spec = alarm.toSpec()
        AlarmEngine.plan(spec, now, snapshot(spec, now.toLocalDate()), holidays)
    }

    suspend fun forecast(spec: AlarmSpec, days: Int): List<ForecastDay> =
        withContext(Dispatchers.IO) {
            val today = LocalDate.now()
            AlarmEngine.forecast(spec, today, days, snapshot(spec, today), holidays = holidays)
        }

    /** Decide si debe sonar. Ante cualquier error, el servicio hace sonar la alarma. */
    suspend fun checkFire(alarmId: Long, date: LocalDate, snoozed: Boolean, backup: Boolean): FireResult =
        withContext(Dispatchers.IO) {
            val alarm = alarms.byId(alarmId) ?: return@withContext FireResult(ring = false)
            if (snoozed || backup) {
                alarms.update(alarm.copy(snoozeUntil = null, backupPending = false))
                return@withContext FireResult(true, alarm.config, backup, alarm.snoozeCount)
            }

            val spec = alarm.toSpec()
            val needsContext = spec.config.rules.any { rule ->
                rule.conditions.any { it is ContextCondition }
            }
            val device = if (needsContext) deviceState.read() else null
            val decision = AlarmEngine.evaluate(spec, date, snapshot(spec, date), device, holidays = holidays)
            when (decision) {
                is Decision.Ring -> {
                    decision.note?.takeIf { it == AlarmEngine.CALENDAR_WARNING }
                        ?.let { log(alarm, HistoryType.WARNING, it) }
                    FireResult(true, alarm.config, false, 0, decision.burst)
                }
                is Decision.Skip -> {
                    logSkip(alarm, date, decision.reason)
                    schedule(alarm, ZonedDateTime.now())
                    FireResult(ring = false)
                }
            }
        }

    suspend fun afterRing(alarmId: Long) = withContext(Dispatchers.IO) {
        val alarm = alarms.byId(alarmId) ?: return@withContext
        log(alarm, HistoryType.RANG)
        val nextToday = plan(alarm).fireAt?.toLocalDate() == LocalDate.now()
        val finished = alarm.config.isOneShot && !(alarm.config.burst != null && nextToday)
        val updated = if (finished) alarm.copy(enabled = false) else alarm
        if (updated != alarm) alarms.update(updated)
        schedule(updated, ZonedDateTime.now())
        refreshWidgets()
    }

    suspend fun finish(alarmId: Long, type: String) = withContext(Dispatchers.IO) {
        val alarm = alarms.byId(alarmId) ?: return@withContext
        log(alarm, type)
        alarms.update(alarm.copy(snoozeCount = 0))
        refreshWidgets()
    }

    /** Cancela las repeticiones que quedaban hoy. Mañana la alarma sigue con normalidad. */
    suspend fun stopForToday(alarmId: Long) = withContext(Dispatchers.IO) {
        val alarm = alarms.byId(alarmId) ?: return@withContext
        val updated = alarm.copy(stoppedDate = LocalDate.now().toString(), snoozeCount = 0)
        alarms.update(updated)
        log(updated, HistoryType.STOPPED)
        val finished = updated.config.isOneShot
        val saved = if (finished) updated.copy(enabled = false) else updated
        if (finished) alarms.update(saved)
        schedule(saved, ZonedDateTime.now())
        refreshWidgets()
    }

    /** Día que cambiaría "Cambiar solo la próxima" y su hora habitual. */
    suspend fun oneTimeTarget(alarm: AlarmEntity): OneTimeTarget? = withContext(Dispatchers.IO) {
        val now = ZonedDateTime.now()
        val spec = alarm.toSpec().copy(oneTime = null)
        val (date, usual) = AlarmEngine.nextScheduledDay(spec, now, snapshot(spec, now.toLocalDate()))
            ?: return@withContext null
        val current = alarm.oneTimeMoment?.takeIf { it.toLocalDate() == date }?.toLocalTime()
        OneTimeTarget(date, usual, current)
    }

    /** Motivo por el que ese día no sonaría según las reglas, o null si sonaría. */
    suspend fun oneTimeConflict(alarm: AlarmEntity, date: LocalDate): String? = withContext(Dispatchers.IO) {
        val spec = alarm.toSpec().copy(oneTime = null)
        val decision = AlarmEngine.evaluate(spec, date, snapshot(spec, date), holidays = holidays)
        (decision as? Decision.Skip)?.reason
    }

    suspend fun setOneTime(alarm: AlarmEntity, moment: LocalDateTime) = withContext(Dispatchers.IO) {
        val current = alarms.byId(alarm.id) ?: return@withContext
        val day = moment.toLocalDate().toString()
        val updated = current.copy(
            oneTime = moment.toString(),
            skipDate = current.skipDate.takeIf { it != day },
            stoppedDate = current.stoppedDate.takeIf { it != day },
        )
        alarms.update(updated)
        log(updated, HistoryType.ONE_TIME, "$day ${"%02d:%02d".format(moment.hour, moment.minute)}")
        schedule(updated, ZonedDateTime.now())
        refreshWidgets()
    }

    suspend fun clearOneTime(alarm: AlarmEntity) = withContext(Dispatchers.IO) {
        val current = alarms.byId(alarm.id) ?: return@withContext
        val updated = current.copy(oneTime = null)
        alarms.update(updated)
        schedule(updated, ZonedDateTime.now())
        refreshWidgets()
    }

    suspend fun snooze(alarmId: Long) = withContext(Dispatchers.IO) {
        val alarm = alarms.byId(alarmId) ?: return@withContext
        val minutes = alarm.config.snooze.minutes
        val updated = alarm.copy(
            snoozeUntil = System.currentTimeMillis() + minutes * 60_000L,
            snoozeCount = alarm.snoozeCount + 1,
            enabled = true,
        )
        alarms.update(updated)
        log(alarm, HistoryType.SNOOZED, "$minutes min")
        schedule(updated, ZonedDateTime.now())
    }

    /** La alarma no se ha apagado: vuelve a sonar más tarde al máximo. */
    suspend fun scheduleBackup(alarmId: Long) = withContext(Dispatchers.IO) {
        val alarm = alarms.byId(alarmId) ?: return@withContext
        val minutes = alarm.config.backup.afterMinutes
        val updated = alarm.copy(
            snoozeUntil = System.currentTimeMillis() + minutes * 60_000L,
            backupPending = true,
            enabled = true,
        )
        alarms.update(updated)
        log(alarm, HistoryType.BACKUP, tr("Volverá a sonar en $minutes min", "Will ring again in $minutes min"))
        schedule(updated, ZonedDateTime.now())
    }

    suspend fun toggleSkipNext(alarm: AlarmEntity) = withContext(Dispatchers.IO) {
        val updated = if (alarm.hasActiveSkip) {
            alarm.copy(skipDate = null)
        } else {
            alarm.copy(skipDate = plan(alarm).fireAt?.toLocalDate()?.toString(), forceDate = null)
        }
        alarms.update(updated)
        schedule(updated, ZonedDateTime.now())
        refreshWidgets()
    }

    suspend fun forceDate(alarmId: Long, date: LocalDate) = withContext(Dispatchers.IO) {
        val alarm = alarms.byId(alarmId) ?: return@withContext
        val stopped = if (alarm.stoppedDate == date.toString()) null else alarm.stoppedDate
        val updated = alarm.copy(forceDate = date.toString(), skipDate = null, stoppedDate = stopped)
        alarms.update(updated)
        log(updated, HistoryType.FORCED, date.toString())
        schedule(updated, ZonedDateTime.now())
        refreshWidgets()
    }

    suspend fun eveningReport(): EveningReport = withContext(Dispatchers.IO) {
        val tomorrow = LocalDate.now().plusDays(1)
        val skipped = mutableListOf<SkippedTomorrow>()
        val changed = mutableListOf<ChangedTomorrow>()
        var unavailable = false

        alarms.all().filter { it.enabled }.forEach { alarm ->
            val spec = alarm.toSpec()
            if (spec.config.isOneShot || !spec.config.runsOn(tomorrow)) return@forEach

            val calendar = snapshot(spec, tomorrow)
            if (spec.config.calendarIds.isNotEmpty() && calendar == CalendarSnapshot.Unavailable) {
                unavailable = true
            }
            val decision = AlarmEngine.evaluate(spec, tomorrow, calendar, holidays = holidays)
            if (decision is Decision.Skip) {
                skipped += SkippedTomorrow(alarm, tomorrow, decision.reason)
                logSkip(alarm, tomorrow, decision.reason)
            } else if (decision is Decision.Ring && spec.oneTime?.toLocalDate() == tomorrow) {
                changed += ChangedTomorrow(alarm, decision.time)
            }
        }
        EveningReport(skipped, unavailable, changed)
    }

    fun refreshNoticeSchedule() {
        scheduleNotice(ZonedDateTime.now())
    }

    private suspend fun schedule(alarm: AlarmEntity, now: ZonedDateTime) {
        val snoozeUntil = alarm.snoozeUntil
            ?.takeIf { alarm.enabled && it > now.toInstant().toEpochMilli() }
        if (snoozeUntil != null) {
            val date = Instant.ofEpochMilli(snoozeUntil).atZone(ZoneId.systemDefault()).toLocalDate()
            scheduler.schedule(alarm.id, snoozeUntil, date, snoozed = true, backup = alarm.backupPending)
            return
        }

        val spec = alarm.toSpec()
        val fireAt = AlarmEngine.plan(spec, now, snapshot(spec, now.toLocalDate()), holidays).fireAt
        if (fireAt == null) {
            scheduler.cancel(alarm.id)
        } else {
            scheduler.schedule(
                alarm.id,
                fireAt.toInstant().toEpochMilli(),
                fireAt.toLocalDate(),
                snoozed = false,
            )
        }
    }

    private fun scheduleNotice(now: ZonedDateTime) {
        if (!settings.noticeEnabled) {
            scheduler.cancelNotice()
            return
        }
        var next = now.toLocalDate()
            .atTime(settings.noticeHour, settings.noticeMinute)
            .atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        scheduler.scheduleNotice(next.toInstant().toEpochMilli())
    }

    private fun snapshot(spec: AlarmSpec, from: LocalDate): CalendarSnapshot =
        calendars.snapshot(spec.config.calendarIds, from, AlarmEngine.HORIZON_DAYS)

    private suspend fun logSkip(alarm: AlarmEntity, date: LocalDate, reason: String) {
        val detail = "$date: $reason"
        if (history.count(alarm.id, HistoryType.SKIPPED, detail) == 0) {
            log(alarm, HistoryType.SKIPPED, detail)
        }
    }

    private suspend fun log(alarm: AlarmEntity, type: String, detail: String = "") {
        val config = alarm.config
        history.insert(
            HistoryEntry(
                timestamp = System.currentTimeMillis(),
                alarmId = alarm.id,
                label = config.label.ifBlank { "%02d:%02d".format(config.hour, config.minute) },
                type = type,
                detail = detail,
            ),
        )
    }
}
