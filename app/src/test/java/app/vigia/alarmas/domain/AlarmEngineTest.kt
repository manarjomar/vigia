package app.vigia.alarmas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEngineTest {
    init {
        Lang.override = true
    }

    private val madrid = ZoneId.of("Europe/Madrid")
    private val monday = LocalDate.of(2026, 11, 2)
    private val workdays = DayOfWeek.values().take(5).toSet()
    private val config = AlarmConfig(hour = 7, minute = 0, days = workdays)
    private val base = AlarmSpec(id = 1, config = config)

    private fun event(
        first: LocalDate,
        last: LocalDate = first,
        title: String = "Vacaciones",
        allDay: Boolean = true,
        calendarId: Long = 5,
        busy: Boolean = true,
        startMillis: Long = 0,
    ) = CalendarEvent(calendarId, title, first, last, allDay, busy, startMillis)

    private fun available(vararg events: CalendarEvent) = CalendarSnapshot.Available(events.toList())

    private fun rule(
        vararg conditions: Condition,
        action: RuleAction = RuleAction.SKIP,
        mode: MatchMode = MatchMode.ANY,
    ) = AlarmRule(id = action.name, action = action, mode = mode, conditions = conditions.toList())

    private fun withRules(vararg rules: AlarmRule) =
        base.copy(config = config.copy(rules = rules.toList()))

    private val vacation = CalendarCondition(setOf(5))

    private fun skips(spec: AlarmSpec, date: LocalDate, calendar: CalendarSnapshot = available(), device: DeviceState? = null) =
        AlarmEngine.evaluate(spec, date, calendar, device, madrid) is Decision.Skip

    @Test
    fun ringsWithoutRules() {
        assertEquals(Decision.Ring(LocalTime.of(7, 0)), AlarmEngine.evaluate(base, monday, available(), zone = madrid))
    }

    @Test
    fun skipsDayCoveredByCalendar() {
        val decision = AlarmEngine.evaluate(withRules(rule(vacation)), monday, available(event(monday)), zone = madrid)
        assertEquals(Decision.Skip("Vacaciones"), decision)
    }

    @Test
    fun ignoresOtherCalendars() {
        assertFalse(skips(withRules(rule(CalendarCondition(setOf(9)))), monday, available(event(monday))))
    }

    @Test
    fun ringsWhenCalendarCannotBeRead() {
        val decision = AlarmEngine.evaluate(withRules(rule(vacation)), monday, CalendarSnapshot.Unavailable, zone = madrid)
        assertEquals(Decision.Ring(LocalTime.of(7, 0), AlarmEngine.CALENDAR_WARNING), decision)
    }

    @Test
    fun onlyRuleWithUnreadableCalendarStillRings() {
        val only = rule(vacation, action = RuleAction.ONLY)
        assertFalse(skips(withRules(only), monday, CalendarSnapshot.Unavailable))
    }

    @Test
    fun timedEventsAreIgnoredWhenAllDayOnly() {
        assertFalse(skips(withRules(rule(vacation)), monday, available(event(monday, allDay = false))))
    }

    @Test
    fun busyOnlyIgnoresFreeEvents() {
        val busy = CalendarCondition(setOf(5), busyOnly = true)
        assertFalse(skips(withRules(rule(busy)), monday, available(event(monday, busy = false))))
    }

    @Test
    fun keywordsIgnoreCaseAndAccents() {
        val keyword = CalendarCondition(setOf(5), keywords = listOf("VACACION"))
        assertTrue(skips(withRules(rule(keyword)), monday, available(event(monday, title = "Vacación en Cádiz"))))
    }

    @Test
    fun excludedRangeIsInclusive() {
        val dates = DatesCondition(listOf(DateRange(monday, monday.plusDays(2))))
        assertTrue(skips(withRules(rule(dates)), monday.plusDays(2)))
        assertFalse(skips(withRules(rule(dates)), monday.plusDays(3)))
    }

    @Test
    fun forceDateBeatsEveryExclusion() {
        val spec = withRules(rule(vacation)).copy(skipDate = monday, forceDate = monday)
        assertFalse(skips(spec, monday, available(event(monday))))
    }

    @Test
    fun manualSkipApplies() {
        assertTrue(skips(base.copy(skipDate = monday), monday))
    }

    @Test
    fun annualDatesRepeatEveryYear() {
        val annual = rule(AnnualCondition(listOf("12-24")))
        assertTrue(skips(withRules(annual), LocalDate.of(2030, 12, 24)))
    }

    @Test
    fun holidaysFollowOfficialAndalusianCalendar2027() {
        val expected = listOf(
            "2027-01-01", "2027-01-06", "2027-03-01", "2027-03-25", "2027-03-26", "2027-05-01",
            "2027-08-16", "2027-10-12", "2027-11-01", "2027-12-06", "2027-12-08", "2027-12-25",
        ).map(LocalDate::parse).toSet()
        assertEquals(expected, Holidays.forYear(2027, andalucia = true).keys)
    }

    @Test
    fun nationalHolidaysOnlyDoNotMoveSundays() {
        val holidays = Holidays.forYear(2027, andalucia = false)
        assertTrue(LocalDate.of(2027, 8, 15) in holidays)
        assertFalse(LocalDate.of(2027, 3, 1) in holidays)
    }

    @Test
    fun alternateWeeksFollowAnchorWeek() {
        val weeks = rule(WeekCycleCondition(monday.plusDays(2), 2))
        val spec = withRules(weeks)
        assertTrue(skips(spec, monday))
        assertFalse(skips(spec, monday.plusDays(7)))
        assertTrue(skips(spec, monday.plusDays(14)))
        assertTrue(skips(spec, monday.minusDays(14)))
    }

    @Test
    fun shiftPatternRepeats() {
        val only = rule(ShiftCondition(monday, "TTD"), action = RuleAction.ONLY)
        val spec = withRules(only)
        assertFalse(skips(spec, monday))
        assertFalse(skips(spec, monday.plusDays(1)))
        assertTrue(skips(spec, monday.plusDays(2)))
        assertFalse(skips(spec, monday.plusDays(3)))
        assertTrue(skips(spec, monday.minusDays(1)))
    }

    @Test
    fun parityAndMonths() {
        assertTrue(skips(withRules(rule(ParityCondition(even = true))), monday))
        assertFalse(skips(withRules(rule(ParityCondition(even = false))), monday))
        assertTrue(skips(withRules(rule(MonthsCondition(setOf(11)))), monday))
        assertFalse(skips(withRules(rule(MonthsCondition(setOf(8)))), monday))
    }

    @Test
    fun onlyRuleNeedsEvent() {
        val office = rule(CalendarCondition(setOf(5), keywords = listOf("Oficina")), action = RuleAction.ONLY)
        assertTrue(skips(withRules(office), monday))
        assertFalse(skips(withRules(office), monday, available(event(monday, title = "Oficina"))))
    }

    @Test
    fun exceptionBeatsSkipRule() {
        val holiday = rule(DatesCondition(listOf(DateRange(monday, monday))))
        val guard = rule(CalendarCondition(setOf(5), keywords = listOf("Guardia")), action = RuleAction.ALWAYS)
        val spec = withRules(holiday, guard)
        assertTrue(skips(spec, monday))
        assertFalse(skips(spec, monday, available(event(monday, title = "Guardia"))))
    }

    @Test
    fun allModeNeedsEveryCondition() {
        val combined = rule(ParityCondition(even = true), MonthsCondition(setOf(8)), mode = MatchMode.ALL)
        assertFalse(skips(withRules(combined), monday))
        val both = rule(ParityCondition(even = true), MonthsCondition(setOf(11)), mode = MatchMode.ALL)
        assertTrue(skips(withRules(both), monday))
    }

    @Test
    fun contextIsPendingUntilFireTime() {
        val charging = rule(ContextCondition(ContextKind.CHARGING))
        val spec = withRules(charging)
        val planned = AlarmEngine.evaluate(spec, monday, available(), zone = madrid)
        assertEquals(Decision.Ring(LocalTime.of(7, 0), AlarmEngine.CONTEXT_NOTE), planned)
        assertTrue(skips(spec, monday, device = DeviceState(charging = true)))
        assertFalse(skips(spec, monday, device = DeviceState(charging = false)))
        assertFalse(skips(spec, monday, device = DeviceState()))
    }

    @Test
    fun wifiAndLocationContext() {
        val wifi = withRules(rule(ContextCondition(ContextKind.WIFI, ssid = "Casa")))
        assertTrue(skips(wifi, monday, device = DeviceState(wifiSsid = "casa")))
        assertFalse(skips(wifi, monday, device = DeviceState(wifiSsid = "Oficina")))

        val place = withRules(rule(ContextCondition(ContextKind.LOCATION, latitude = 37.39, longitude = -4.77, radiusMeters = 500)))
        assertTrue(skips(place, monday, device = DeviceState(latitude = 37.391, longitude = -4.77)))
        assertFalse(skips(place, monday, device = DeviceState(latitude = 37.5, longitude = -4.77)))
    }

    @Test
    fun relativeAlarmUsesFirstTimedEvent() {
        val calendarConfig = config.copy(relative = RelativeEvent(setOf(5), minutesBefore = 90))
        val spec = base.copy(config = calendarConfig)
        val early = monday.atTime(9, 0).atZone(madrid).toInstant().toEpochMilli()
        val late = monday.atTime(12, 0).atZone(madrid).toInstant().toEpochMilli()
        val snapshot = available(
            event(monday, allDay = false, title = "Tarde", startMillis = late),
            event(monday, allDay = false, title = "Primera", startMillis = early),
        )
        val decision = AlarmEngine.evaluate(spec, monday, snapshot, zone = madrid)
        assertEquals(Decision.Ring(LocalTime.of(7, 30), "Por Primera"), decision)
    }

    @Test
    fun relativeAlarmWithoutEvent() {
        val strict = base.copy(config = config.copy(relative = RelativeEvent(setOf(5))))
        assertTrue(skips(strict, monday))
        val fallback = base.copy(config = config.copy(relative = RelativeEvent(setOf(5), useBaseTimeWhenEmpty = true)))
        assertEquals(Decision.Ring(LocalTime.of(7, 0)), AlarmEngine.evaluate(fallback, monday, available(), zone = madrid))
    }

    @Test
    fun relativeAlarmFallsBackWhenCalendarFails() {
        val spec = base.copy(config = config.copy(relative = RelativeEvent(setOf(5))))
        val decision = AlarmEngine.evaluate(spec, monday, CalendarSnapshot.Unavailable, zone = madrid)
        assertEquals(Decision.Ring(LocalTime.of(7, 0), AlarmEngine.CALENDAR_WARNING), decision)
    }

    @Test
    fun planJumpsToNextFreeWorkday() {
        val friday = monday.plusDays(4)
        val now = monday.atTime(6, 0).atZone(madrid)
        val plan = AlarmEngine.plan(withRules(rule(vacation)), now, available(event(monday, friday)))
        assertEquals(monday.plusDays(7), plan.fireAt?.toLocalDate())
        assertEquals(5, plan.skipped.size)
    }

    @Test
    fun planIgnoresTodayWhenTimeHasPassed() {
        val now = monday.atTime(7, 0).atZone(madrid)
        assertEquals(monday.plusDays(1), AlarmEngine.plan(base, now, available()).fireAt?.toLocalDate())
    }

    @Test
    fun oneShotPicksNextOccurrenceOnly() {
        val oneShot = base.copy(config = config.copy(days = emptySet()))
        val now = monday.atTime(8, 0).atZone(madrid)
        assertEquals(monday.plusDays(1), AlarmEngine.plan(oneShot, now, available()).fireAt?.toLocalDate())

        val excluded = oneShot.copy(
            config = oneShot.config.copy(
                rules = listOf(rule(DatesCondition(listOf(DateRange(monday.plusDays(1), monday.plusDays(1)))))),
            ),
        )
        assertNull(AlarmEngine.plan(excluded, now, available()).fireAt)
    }

    @Test
    fun disabledAlarmHasNoPlan() {
        val now = monday.atTime(6, 0).atZone(madrid)
        assertNull(AlarmEngine.plan(base.copy(enabled = false), now, available()).fireAt)
    }

    @Test
    fun allDayEventsUseUtcAndExclusiveEnd() {
        val begin = LocalDate.of(2026, 12, 24).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 12, 26).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dates = AlarmEngine.eventDates(begin, end, true, ZoneId.of("America/Los_Angeles"))
        assertEquals(LocalDate.of(2026, 12, 24) to LocalDate.of(2026, 12, 25), dates)
    }

    @Test
    fun timedEventEndingAtMidnightDoesNotCoverNextDay() {
        val begin = monday.atTime(20, 0).atZone(madrid).toInstant().toEpochMilli()
        val end = monday.plusDays(1).atStartOfDay(madrid).toInstant().toEpochMilli()
        assertEquals(monday to monday, AlarmEngine.eventDates(begin, end, false, madrid))
    }

    @Test
    fun skipsAcrossSpringForwardGap() {
        val sunday = LocalDate.of(2027, 3, 28)
        val spec = base.copy(config = config.copy(hour = 2, minute = 30, days = setOf(DayOfWeek.SUNDAY)))
        val now = sunday.minusDays(1).atTime(12, 0).atZone(madrid)
        assertEquals(sunday.atTime(3, 30).atZone(madrid), AlarmEngine.plan(spec, now, available()).fireAt)
    }

    @Test
    fun configSurvivesJsonRoundTrip() {
        val full = config.copy(
            label = "Trabajo",
            rules = listOf(
                rule(vacation, HolidayCondition(), ContextCondition(ContextKind.WIFI, ssid = "Casa")),
                rule(WeekCycleCondition(monday), ShiftCondition(monday), action = RuleAction.ONLY),
                rule(DatesCondition(listOf(DateRange(monday, monday))), action = RuleAction.ALWAYS),
            ),
            relative = RelativeEvent(setOf(5)),
            challenge = ChallengeConfig(ChallengeKind.QR, qrValue = "abc"),
        )
        val text = AppJson.encodeToString(AlarmConfig.serializer(), full)
        assertEquals(full, AppJson.decodeFromString(AlarmConfig.serializer(), text))
    }

    @Test
    fun oldJsonGetsDefaults() {
        val decoded = AppJson.decodeFromString(AlarmConfig.serializer(), """{"label":"A","campoNuevo":1}""")
        assertEquals("A", decoded.label)
        assertEquals(30, decoded.sound.fadeSeconds)
    }
}
