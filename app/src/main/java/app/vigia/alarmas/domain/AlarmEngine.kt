package app.vigia.alarmas.domain

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface Decision {
    data class Ring(
        val time: LocalTime,
        val note: String? = null,
        val burst: BurstConfig? = null,
    ) : Decision
    data class Skip(val reason: String) : Decision
}

data class SkippedDay(val date: LocalDate, val reason: String)

data class Plan(val fireAt: ZonedDateTime?, val skipped: List<SkippedDay>, val note: String?)

data class ForecastDay(val date: LocalDate, val decision: Decision)

private sealed interface Match {
    data class Yes(val reason: String) : Match
    data object No : Match
    data class Unknown(val note: String) : Match
}

object AlarmEngine {
    const val HORIZON_DAYS = 370L
    val CALENDAR_WARNING: String get() = tr("No se pudo leer el calendario: la alarma sonará", "Couldn't read the calendar: the alarm will ring")
    val CONTEXT_NOTE: String get() = tr("Se decide al llegar la hora", "Decided when the time comes")
    val ONE_TIME_NOTE: String get() = tr("Solo esta vez", "Just this once")
    val HOLIDAY_NOTE: String get() = tr("Sin datos de festivos: la alarma sonará", "No holiday data: the alarm will ring")

    /**
     * Decide un día concreto. Con [device] nulo no se conoce el contexto: esas reglas no pueden
     * saltar la alarma y se vuelven a evaluar al llegar la hora. Si falta un dato, la alarma suena.
     */
    fun evaluate(
        spec: AlarmSpec,
        date: LocalDate,
        calendar: CalendarSnapshot,
        device: DeviceState? = null,
        zone: ZoneId = ZoneId.systemDefault(),
        holidays: HolidayLookup = OfflineHolidays,
    ): Decision {
        val time = timeFor(spec.config, date, calendar, zone)
        val baseNote = (time as? Timed.Ready)?.note
        val burst = (time as? Timed.Ready)?.burst ?: spec.config.burst.takeIf { spec.config.shifts == null }

        if (spec.stoppedDate == date) return Decision.Skip(tr("Detenida por hoy", "Stopped for today"))
        if (spec.skipDate == date) return Decision.Skip(tr("Saltada manualmente", "Skipped manually"))
        spec.oneTime?.takeIf { it.toLocalDate() == date }?.let { moment ->
            val usual = resolved(spec.config, time)
            return Decision.Ring(moment.toLocalTime(), ONE_TIME_NOTE, shiftBurst(burst, usual, moment.toLocalTime()))
        }
        if (spec.forceDate == date) return Decision.Ring(resolved(spec.config, time), baseNote, burst)
        if (time is Timed.NoEvent) return Decision.Skip(tr("No hay ningún evento ese día", "No events that day"))
        if (time is Timed.Rest) return Decision.Skip(time.reason)

        val results = spec.config.rules.filter { it.conditions.isNotEmpty() }
            .map { it to evaluateRule(it, date, calendar, device, holidays) }

        val ringTime = resolved(spec.config, time)
        if (results.any { (rule, match) -> rule.action == RuleAction.ALWAYS && match is Match.Yes }) {
            return Decision.Ring(ringTime, baseNote, burst)
        }

        results.firstOrNull { (rule, match) -> rule.action == RuleAction.SKIP && match is Match.Yes }
            ?.let { (_, match) -> return Decision.Skip((match as Match.Yes).reason) }

        results.firstOrNull { (rule, match) -> rule.action == RuleAction.ONLY && match == Match.No }
            ?.let { (rule, _) -> return Decision.Skip(tr("No se cumple: ${describe(rule)}", "Not met: ${describe(rule)}")) }

        val note = baseNote ?: results.firstNotNullOfOrNull { (_, match) -> (match as? Match.Unknown)?.note }
        return Decision.Ring(ringTime, note, burst)
    }

    fun plan(
        spec: AlarmSpec,
        now: ZonedDateTime,
        calendar: CalendarSnapshot,
        holidays: HolidayLookup = OfflineHolidays,
    ): Plan {
        if (!spec.enabled) return Plan(null, emptyList(), null)

        val skipped = mutableListOf<SkippedDay>()
        for (offset in 0..HORIZON_DAYS) {
            val date = now.toLocalDate().plusDays(offset)
            if (!spec.config.runsOn(date)) continue

            when (val decision = evaluate(spec, date, calendar, zone = now.zone, holidays = holidays)) {
                is Decision.Ring -> {
                    val candidate = occurrences(decision)
                        .map { fireMoment(date, it, now.zone) }
                        .firstOrNull { it.isAfter(now) }
                    if (candidate != null) return Plan(candidate, skipped, decision.note)
                }
                is Decision.Skip -> {
                    val moment = date.atTime(spec.config.hour, spec.config.minute).atZone(now.zone)
                    if (moment.isAfter(now)) {
                        skipped += SkippedDay(date, decision.reason)
                        if (spec.config.isOneShot) return Plan(null, skipped, null)
                    }
                }
            }
        }
        return Plan(null, skipped, null)
    }

    fun forecast(
        spec: AlarmSpec,
        from: LocalDate,
        days: Int,
        calendar: CalendarSnapshot,
        zone: ZoneId = ZoneId.systemDefault(),
        holidays: HolidayLookup = OfflineHolidays,
    ): List<ForecastDay> = (0 until days).mapNotNull { offset ->
        val date = from.plusDays(offset.toLong())
        if (!spec.config.runsOn(date)) {
            null
        } else {
            ForecastDay(date, evaluate(spec, date, calendar, zone = zone, holidays = holidays))
        }
    }

    /**
     * Día y hora habitual de la próxima vez que toca la alarma, sin aplicar reglas.
     * Es el día que cambia "Cambiar solo la próxima".
     */
    fun nextScheduledDay(
        spec: AlarmSpec,
        now: ZonedDateTime,
        calendar: CalendarSnapshot,
    ): Pair<LocalDate, LocalTime>? {
        for (offset in 0..HORIZON_DAYS) {
            val date = now.toLocalDate().plusDays(offset)
            if (!spec.config.runsOn(date)) continue
            val usual = resolved(spec.config, timeFor(spec.config, date, calendar, now.zone))
            if (offset == 0L && !usual.isAfter(now.toLocalTime())) continue
            return date to usual
        }
        return null
    }

    /** Mantiene la duración del rango de repetición al mover la hora de inicio. */
    fun shiftBurst(burst: BurstConfig?, from: LocalTime, to: LocalTime): BurstConfig? {
        if (burst == null) return null
        val start = from.hour * 60 + from.minute
        val length = burst.endHour * 60 + burst.endMinute - start
        if (length <= 0) return burst
        val end = (to.hour * 60 + to.minute + length).coerceAtMost(23 * 60 + 59)
        return burst.copy(endHour = end / 60, endMinute = end % 60)
    }

    /** Horas a las que suena ese día: una, o todas las del rango. */
    fun occurrences(ring: Decision.Ring): List<LocalTime> {
        val burst = ring.burst ?: return listOf(ring.time)
        val first = ring.time.toSecondOfDay() / 60
        val last = burst.endHour * 60 + burst.endMinute
        val step = burst.everyMinutes.coerceAtLeast(1)
        if (last <= first) return listOf(ring.time)
        return (first..last step step).map { LocalTime.of(it / 60, it % 60) }
    }

    /**
     * Android guarda los eventos de día completo en UTC y con fin exclusivo. Los eventos con
     * hora usan la zona local; un evento que termina justo a medianoche no ocupa el día siguiente.
     */
    fun eventDates(
        beginMillis: Long,
        endMillis: Long,
        allDay: Boolean,
        zone: ZoneId,
    ): Pair<LocalDate, LocalDate> {
        val effectiveZone = if (allDay) ZoneOffset.UTC else zone
        val first = Instant.ofEpochMilli(beginMillis).atZone(effectiveZone).toLocalDate()
        val safeEnd = maxOf(beginMillis, endMillis - 1)
        val last = Instant.ofEpochMilli(safeEnd).atZone(effectiveZone).toLocalDate()
        return first to last
    }

    fun describe(rule: AlarmRule): String =
        rule.conditions.joinToString(if (rule.mode == MatchMode.ALL) " y " else " o ") { describe(it) }

    fun describe(condition: Condition): String = when (condition) {
        is CalendarCondition -> if (condition.keywords.isEmpty()) {
            tr("hay un evento en el calendario", "there is a calendar event")
        } else {
            tr("hay un evento con ${condition.keywords.joinToString(", ")}", "there is an event with ${condition.keywords.joinToString(", ")}")
        }
        is DatesCondition -> tr("fechas elegidas", "chosen dates")
        is AnnualCondition -> tr("fechas anuales", "yearly dates")
        is HolidayCondition -> tr("festivo en ${regionName(condition)}", "holiday in ${regionName(condition)}")
        is WeekCycleCondition -> tr("una de cada ${condition.everyWeeks} semanas", "one in every ${condition.everyWeeks} weeks")
        is ShiftCondition -> tr("día de turno", "shift day")
        is ParityCondition -> if (condition.even) tr("día par", "even day") else tr("día impar", "odd day")
        is MonthsCondition -> tr("meses elegidos", "chosen months")
        is ContextCondition -> when (condition.kind) {
            ContextKind.WIFI -> tr("conectado a ${condition.ssid.ifBlank { "la wifi elegida" }}", "connected to ${condition.ssid.ifBlank { "the chosen Wi-Fi" }}")
            ContextKind.LOCATION -> tr("en ${condition.placeName.ifBlank { "el lugar elegido" }}", "at ${condition.placeName.ifBlank { "the chosen place" }}")
            ContextKind.AIRPLANE -> tr("modo avión activo", "airplane mode on")
            ContextKind.DO_NOT_DISTURB -> tr("No molestar activo", "Do Not Disturb on")
            ContextKind.CHARGING -> tr("móvil cargando", "phone charging")
        }
    }

    fun regionName(condition: HolidayCondition): String {
        val subdivision = condition.effectiveSubdivision
        val country = condition.effectiveCountry
        return when {
            subdivision == OfflineHolidays.ANDALUCIA -> "Andalucía"
            subdivision.isNotBlank() -> subdivision
            country.isBlank() -> tr("el país elegido", "the chosen country")
            else -> Locale("", country).getDisplayCountry(APP_LOCALE)
        }
    }

    private sealed interface Timed {
        data class Ready(
            val time: LocalTime,
            val note: String? = null,
            val burst: BurstConfig? = null,
        ) : Timed
        data class Rest(val reason: String) : Timed
        data object Base : Timed
        data object NoEvent : Timed
    }

    private fun resolved(config: AlarmConfig, timed: Timed): LocalTime =
        (timed as? Timed.Ready)?.time ?: LocalTime.of(config.hour, config.minute)

    private fun fireMoment(
        date: LocalDate,
        time: LocalTime,
        zone: ZoneId,
    ): ZonedDateTime = date.atTime(time).atZone(zone)

    private fun timeFor(
        config: AlarmConfig,
        date: LocalDate,
        calendar: CalendarSnapshot,
        zone: ZoneId,
    ): Timed {
        val base = LocalTime.of(config.hour, config.minute)
        config.shifts?.let { return shiftTime(it, base, date, calendar) }
        val relative = config.relative ?: return Timed.Base
        if (calendar !is CalendarSnapshot.Available) {
            return Timed.Ready(base, CALENDAR_WARNING)
        }

        val first = calendar.events
            .filter { event ->
                !event.allDay &&
                    event.calendarId in relative.calendarIds &&
                    event.firstDate == date &&
                    keywordsMatch(relative.keywords, event.title)
            }
            .minByOrNull { it.startMillis }

        if (first == null) {
            return if (relative.useBaseTimeWhenEmpty) Timed.Ready(base) else Timed.NoEvent
        }

        val start: LocalDateTime = Instant.ofEpochMilli(first.startMillis).atZone(zone).toLocalDateTime()
        val moment = start.minusMinutes(relative.minutesBefore.toLong())
        val time = if (moment.toLocalDate() == date) moment.toLocalTime() else LocalTime.MIN
        return Timed.Ready(time, tr("Por ${first.title.ifBlank { "el primer evento" }}", "For ${first.title.ifBlank { "the first event" }}"))
    }

    private fun shiftTime(
        plan: ShiftPlan,
        base: LocalTime,
        date: LocalDate,
        calendar: CalendarSnapshot,
    ): Timed = when (val slot = ShiftPlanner.slot(plan, date, calendar)) {
        is ShiftSlot.Work -> Timed.Ready(
            LocalTime.of(slot.type.hour, slot.type.minute),
            slot.type.name,
            slot.type.burst,
        )
        ShiftSlot.Rest -> Timed.Rest(tr("Descanso", "Day off"))
        ShiftSlot.Unassigned -> when {
            plan.calendarIds.isNotEmpty() && calendar is CalendarSnapshot.Unavailable ->
                Timed.Ready(base, CALENDAR_WARNING)
            plan.unassignedRings -> Timed.Ready(
                base,
                tr("Sin turno asignado: suena a la hora normal", "No shift assigned: rings at the usual time"),
            )
            else -> Timed.Rest(tr("Sin turno asignado", "No shift assigned"))
        }
    }

    private fun evaluateRule(
        rule: AlarmRule,
        date: LocalDate,
        calendar: CalendarSnapshot,
        device: DeviceState?,
        holidays: HolidayLookup,
    ): Match {
        val matches = rule.conditions.map { evaluateCondition(it, date, calendar, device, holidays) }
        val unknown = matches.filterIsInstance<Match.Unknown>().firstOrNull()
        return when (rule.mode) {
            MatchMode.ANY -> matches.firstOrNull { it is Match.Yes } ?: unknown ?: Match.No
            MatchMode.ALL -> when {
                matches.any { it == Match.No } -> Match.No
                unknown != null -> unknown
                else -> Match.Yes(
                    matches.filterIsInstance<Match.Yes>().joinToString(" y ") { it.reason },
                )
            }
        }
    }

    private fun evaluateCondition(
        condition: Condition,
        date: LocalDate,
        calendar: CalendarSnapshot,
        device: DeviceState?,
        holidays: HolidayLookup,
    ): Match = when (condition) {
        is CalendarCondition -> calendarMatch(condition, date, calendar)
        is DatesCondition -> yesIf(condition.ranges.any { date in it }, tr("Fechas excluidas", "Excluded dates"))
        is AnnualCondition -> yesIf(
            "%02d-%02d".format(date.monthValue, date.dayOfMonth) in condition.dates,
            tr("Fecha anual", "Yearly date"),
        )
        is HolidayCondition -> {
            val answer = holidays.find(condition.effectiveCountry, condition.effectiveSubdivision, date)
            when (answer) {
                is HolidayAnswer.Named -> Match.Yes(tr("Festivo: ${answer.name}", "Holiday: ${answer.name}"))
                HolidayAnswer.None -> Match.No
                HolidayAnswer.Unknown -> Match.Unknown(HOLIDAY_NOTE)
            }
        }
        is WeekCycleCondition -> {
            val weekField = WeekFields.ISO.dayOfWeek()
            val anchorMonday = condition.anchor.with(weekField, 1)
            val dateMonday = date.with(weekField, 1)
            val weeks = ChronoUnit.WEEKS.between(anchorMonday, dateMonday)
            yesIf(Math.floorMod(weeks, condition.everyWeeks.coerceAtLeast(1).toLong()) == 0L, tr("Semana alterna", "Alternate week"))
        }
        is ShiftCondition -> {
            val pattern = condition.pattern.uppercase().filter { it == 'T' || it == 'D' }
            if (pattern.isEmpty()) {
                Match.No
            } else {
                val day = ChronoUnit.DAYS.between(condition.anchor, date)
                val index = Math.floorMod(day, pattern.length.toLong()).toInt()
                yesIf(pattern[index] == 'T', tr("Día de turno", "Shift day"))
            }
        }
        is ParityCondition -> yesIf(
            (date.dayOfMonth % 2 == 0) == condition.even,
            if (condition.even) tr("Día par", "Even day") else tr("Día impar", "Odd day"),
        )
        is MonthsCondition -> yesIf(date.monthValue in condition.months, tr("Mes excluido", "Excluded month"))
        is ContextCondition -> contextMatch(condition, device)
    }

    private fun calendarMatch(
        condition: CalendarCondition,
        date: LocalDate,
        calendar: CalendarSnapshot,
    ): Match = when (calendar) {
        CalendarSnapshot.Unavailable -> Match.Unknown(CALENDAR_WARNING)
        is CalendarSnapshot.Available -> {
            val event = calendar.events.firstOrNull { event ->
                event.calendarId in condition.calendarIds &&
                    (!condition.allDayOnly || event.allDay) &&
                    (!condition.busyOnly || event.busy) &&
                    !date.isBefore(event.firstDate) &&
                    !date.isAfter(event.lastDate) &&
                    keywordsMatch(condition.keywords, event.title)
            }
            if (event == null) Match.No else Match.Yes(event.title.ifBlank { tr("Evento de calendario", "Calendar event") })
        }
    }

    private fun contextMatch(condition: ContextCondition, device: DeviceState?): Match {
        if (device == null) return Match.Unknown(CONTEXT_NOTE)
        val label = describe(condition).replaceFirstChar { it.uppercase() }
        val value: Boolean? = when (condition.kind) {
            ContextKind.CHARGING -> device.charging
            ContextKind.AIRPLANE -> device.airplane
            ContextKind.DO_NOT_DISTURB -> device.doNotDisturb
            ContextKind.WIFI -> device.wifiSsid?.equals(condition.ssid.trim(), ignoreCase = true)
            ContextKind.LOCATION -> {
                val latitude = device.latitude
                val longitude = device.longitude
                if (latitude == null || longitude == null) {
                    null
                } else {
                    distanceMeters(latitude, longitude, condition.latitude, condition.longitude) <=
                        condition.radiusMeters
                }
            }
        }
        return when (value) {
            true -> Match.Yes(label)
            false -> Match.No
            null -> Match.Unknown(tr("Sin datos de contexto: la alarma sonará", "No phone data: the alarm will ring"))
        }
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * radius * asin(sqrt(a))
    }

    private fun yesIf(value: Boolean, reason: String): Match = if (value) Match.Yes(reason) else Match.No

    internal fun keywordsMatch(keywords: List<String>, title: String): Boolean {
        if (keywords.isEmpty()) return true
        val normalized = normalize(title)
        return keywords.any { normalized.contains(normalize(it)) }
    }

    internal fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
}
