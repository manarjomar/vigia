package app.vigia.alarmas.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanningTest {
    init {
        Lang.override = true
    }

    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun bedtimesCountCyclesAndFallingAsleep() {
        val wake = today.atTime(7, 0).atZone(ZoneId.of("Europe/Madrid"))
        val first = SleepPlanner.bedtimes(wake).first()
        assertEquals(today.minusDays(1).atTime(21, 45), first.time.toLocalDateTime())
        assertEquals(6, first.cycles)
    }

    @Test
    fun statsUseLastDismissalOfEachDay() {
        val points = listOf(
            HistoryPoint(today, LocalTime.of(7, 0), SleepAnalytics.SNOOZED),
            HistoryPoint(today, LocalTime.of(7, 10), SleepAnalytics.DISMISSED),
            HistoryPoint(today.minusDays(1), LocalTime.of(7, 30), SleepAnalytics.DISMISSED),
            HistoryPoint(today, LocalTime.of(21, 0), SleepAnalytics.SKIPPED),
            HistoryPoint(today.minusDays(30), LocalTime.of(5, 0), SleepAnalytics.DISMISSED),
        )
        val stats = SleepAnalytics.build(points, today)
        assertEquals(2, stats.records.size)
        assertEquals(LocalTime.of(7, 20), stats.averageWake)
        assertEquals(0.5, stats.averageSnoozes, 0.001)
        assertEquals(1, stats.skipped)
    }

    @Test
    fun emptyStats() {
        assertNull(SleepAnalytics.build(emptyList(), today).averageWake)
    }

    @Test
    fun timerChainAdvancesThroughLoops() {
        val first = TimerPlan.Position(0, 0)
        assertEquals(TimerPlan.Position(1, 0), TimerPlan.next(first, 2, 2))
        assertEquals(TimerPlan.Position(0, 1), TimerPlan.next(TimerPlan.Position(1, 0), 2, 2))
        assertNull(TimerPlan.next(TimerPlan.Position(1, 1), 2, 2))
    }

    @Test
    fun durationsFormat() {
        assertEquals("01:05", Durations.clock(65_000))
        assertEquals("1:00:00", Durations.clock(3_600_000))
        assertEquals("00:01,50", Durations.clock(1_500, withHundredths = true))
    }

    private val sample = """
        [
          {"date":"2027-01-01","localName":"Capodanno","name":"New Year","global":true,"counties":null,"types":["Public"]},
          {"date":"2027-06-24","localName":"San Giovanni","name":"St John","global":false,"counties":["IT-25","IT-21"],"types":["Public"]},
          {"date":"2027-07-04","localName":"Banca","name":"Bank","global":true,"counties":null,"types":["Bank"],"extra":1}
        ]
    """

    @Test
    fun publicHolidaysFilterTypesAndRegions() {
        val holidays = PublicHolidays.parse(sample)
        assertEquals(2, holidays.size)
        assertEquals(listOf("IT-21", "IT-25"), PublicHolidays.regions(holidays))

        val national = LocalDate.of(2027, 1, 1)
        val regional = LocalDate.of(2027, 6, 24)
        assertEquals(HolidayAnswer.Named("Capodanno"), PublicHolidays.find(holidays, "", national))
        assertEquals(HolidayAnswer.Named("Capodanno"), PublicHolidays.find(holidays, "IT-21", national))
        assertEquals(HolidayAnswer.None, PublicHolidays.find(holidays, "", regional))
        assertEquals(HolidayAnswer.None, PublicHolidays.find(holidays, "IT-99", regional))
        assertEquals(HolidayAnswer.Named("San Giovanni"), PublicHolidays.find(holidays, "IT-25", regional))
    }

    @Test
    fun otherCountryWithoutDataRingsWithNote() {
        val rule = AlarmRule("f", conditions = listOf(HolidayCondition(country = "IT")))
        val spec = AlarmSpec(1, config = AlarmConfig(days = setOf(today.dayOfWeek), rules = listOf(rule)))
        val decision = AlarmEngine.evaluate(spec, today, CalendarSnapshot.Available(emptyList()))
        assertEquals(Decision.Ring(LocalTime.of(7, 0), AlarmEngine.HOLIDAY_NOTE), decision)
    }

    @Test
    fun cachedHolidayFromAnotherCountrySkips() {
        val rule = AlarmRule("f", conditions = listOf(HolidayCondition(country = "IT")))
        val spec = AlarmSpec(1, config = AlarmConfig(days = setOf(today.dayOfWeek), rules = listOf(rule)))
        val lookup = HolidayLookup { _, _, _ -> HolidayAnswer.Named("Festa") }
        val decision = AlarmEngine.evaluate(spec, today, CalendarSnapshot.Available(emptyList()), holidays = lookup)
        assertEquals(Decision.Skip("Festivo: Festa"), decision)
    }

    @Test
    fun earlierAndaluciaRuleKeepsWorking() {
        val old = AppJson.decodeFromString(
            AlarmConfig.serializer(),
            """{"rules":[{"id":"a","conditions":[{"tipo":"festivos","andalucia":true}]}]}""",
        )
        val condition = old.rules.first().conditions.first() as HolidayCondition
        assertEquals("ES", condition.effectiveCountry)
        assertEquals(OfflineHolidays.ANDALUCIA, condition.effectiveSubdivision)
        assertEquals("Andalucía", AlarmEngine.regionName(condition))
    }

    @Test
    fun englishMessagesFollowLanguage() {
        Lang.override = false
        try {
            val spec = AlarmSpec(1, config = AlarmConfig(days = setOf(today.dayOfWeek)), skipDate = today)
            val decision = AlarmEngine.evaluate(spec, today, CalendarSnapshot.Available(emptyList()))
            assertEquals(Decision.Skip("Skipped manually"), decision)
            assertEquals("Christmas Day", Holidays.nameOf(LocalDate.of(2027, 12, 25), true))
        } finally {
            Lang.override = true
        }
    }

    private val zone = ZoneId.of("Europe/Madrid")
    private val monday = LocalDate.of(2026, 11, 2)
    private val morning = ShiftType("m", "Mañana", 6, 0)
    private val afternoon = ShiftType("t", "Tarde", 13, 0)
    private val week = { id: String -> List(5) { id } + listOf("", "") }
    private val threeWeeks = ShiftPlan(
        types = listOf(morning, afternoon),
        cycle = week("m") + week("t") + List(7) { "" },
        start = monday,
    )
    private val empty = CalendarSnapshot.Available(emptyList())

    private fun shiftSpec(plan: ShiftPlan) = AlarmSpec(1, config = AlarmConfig(shifts = plan))

    @Test
    fun threeWeekCycleUsesEachShiftTimeAndRepeats() {
        val spec = shiftSpec(threeWeeks)
        assertEquals(Decision.Ring(LocalTime.of(6, 0), "Mañana"), AlarmEngine.evaluate(spec, monday, empty))
        assertEquals(Decision.Ring(LocalTime.of(13, 0), "Tarde"), AlarmEngine.evaluate(spec, monday.plusDays(7), empty))
        assertEquals(Decision.Skip("Descanso"), AlarmEngine.evaluate(spec, monday.plusDays(14), empty))
        assertEquals(Decision.Ring(LocalTime.of(6, 0), "Mañana"), AlarmEngine.evaluate(spec, monday.plusDays(21), empty))
        assertEquals(Decision.Skip("Descanso"), AlarmEngine.evaluate(spec, monday.minusDays(7), empty))
    }

    @Test
    fun planWaitsForNextShiftAcrossRestWeek() {
        val now = monday.plusDays(11).atTime(14, 0).atZone(zone)
        val fire = AlarmEngine.plan(shiftSpec(threeWeeks), now, empty).fireAt
        assertEquals(monday.plusDays(21).atTime(6, 0).atZone(zone), fire)
    }

    @Test
    fun overrideBeatsCycleAndCalendar() {
        val date = monday.toString()
        val swapped = threeWeeks.copy(overrides = mapOf(date to "t"), calendarIds = setOf(5))
        val calendar = CalendarSnapshot.Available(listOf(CalendarEvent(5, "Mañana", monday, monday, true)))
        assertEquals(ShiftSlot.Work(afternoon), ShiftPlanner.slot(swapped, monday, calendar))
        val rest = threeWeeks.copy(overrides = mapOf(date to ""))
        assertEquals(ShiftSlot.Rest, ShiftPlanner.slot(rest, monday, empty))
    }

    @Test
    fun calendarEventChoosesShiftWithoutCycle() {
        val plan = ShiftPlan(types = listOf(morning, afternoon), calendarIds = setOf(5), start = monday)
        val calendar = CalendarSnapshot.Available(listOf(CalendarEvent(5, "Turno de TARDE", monday, monday, false)))
        assertEquals(ShiftSlot.Work(afternoon), ShiftPlanner.slot(plan, monday, calendar))
        assertEquals(ShiftSlot.Unassigned, ShiftPlanner.slot(plan, monday.plusDays(1), calendar))
    }

    @Test
    fun unassignedDayFollowsChoiceAndUnreadableCalendarRings() {
        val plan = ShiftPlan(types = listOf(morning), calendarIds = setOf(5), start = monday)
        assertTrue(AlarmEngine.evaluate(shiftSpec(plan), monday, empty) is Decision.Ring)
        val silent = plan.copy(unassignedRings = false)
        assertTrue(AlarmEngine.evaluate(shiftSpec(silent), monday, empty) is Decision.Skip)
        val failed = AlarmEngine.evaluate(shiftSpec(silent), monday, CalendarSnapshot.Unavailable)
        assertEquals(Decision.Ring(LocalTime.of(7, 0), AlarmEngine.CALENDAR_WARNING), failed)
    }

    @Test
    fun rulesStillApplyOverShifts() {
        val holiday = AlarmRule("f", conditions = listOf(DatesCondition(listOf(DateRange(monday, monday)))))
        val spec = AlarmSpec(1, config = AlarmConfig(shifts = threeWeeks, rules = listOf(holiday)))
        assertTrue(AlarmEngine.evaluate(spec, monday, empty) is Decision.Skip)
    }

    @Test
    fun burstRingsEveryIntervalUntilEnd() {
        val burst = BurstConfig(7, 0, 10)
        val spec = AlarmSpec(1, config = AlarmConfig(hour = 6, minute = 30, days = setOf(monday.dayOfWeek), burst = burst))
        val ring = AlarmEngine.evaluate(spec, monday, empty) as Decision.Ring
        assertEquals(
            listOf(LocalTime.of(6, 30), LocalTime.of(6, 40), LocalTime.of(6, 50), LocalTime.of(7, 0)),
            AlarmEngine.occurrences(ring),
        )
        val now = monday.atTime(6, 41).atZone(zone)
        assertEquals(monday.atTime(6, 50).atZone(zone), AlarmEngine.plan(spec, now, empty).fireAt)
        val after = monday.atTime(7, 0).atZone(zone)
        assertEquals(monday.plusDays(7).atTime(6, 30).atZone(zone), AlarmEngine.plan(spec, after, empty).fireAt)
    }

    @Test
    fun stopForTodayCancelsRemainingButNotNextWeek() {
        val burst = BurstConfig(7, 0, 10)
        val base = AlarmConfig(hour = 6, minute = 30, days = setOf(monday.dayOfWeek), burst = burst)
        val spec = AlarmSpec(1, config = base, stoppedDate = monday)
        val now = monday.atTime(6, 41).atZone(zone)
        assertEquals(monday.plusDays(7).atTime(6, 30).atZone(zone), AlarmEngine.plan(spec, now, empty).fireAt)
    }

    @Test
    fun shiftCanHaveItsOwnBurst() {
        val lazy = morning.copy(burst = BurstConfig(6, 30, 5))
        val plan = threeWeeks.copy(types = listOf(lazy, afternoon))
        val ring = AlarmEngine.evaluate(shiftSpec(plan), monday, empty) as Decision.Ring
        assertEquals(7, AlarmEngine.occurrences(ring).size)
        val afternoonRing = AlarmEngine.evaluate(shiftSpec(plan), monday.plusDays(7), empty) as Decision.Ring
        assertEquals(1, AlarmEngine.occurrences(afternoonRing).size)
    }

    @Test
    fun invalidRangeRingsOnce() {
        val ring = Decision.Ring(LocalTime.of(8, 0), burst = BurstConfig(7, 0, 5))
        assertEquals(listOf(LocalTime.of(8, 0)), AlarmEngine.occurrences(ring))
    }

    @Test
    fun shiftPlanSurvivesJson() {
        val config = AlarmConfig(shifts = threeWeeks.copy(overrides = mapOf("2026-11-03" to "t")))
        val text = AppJson.encodeToString(AlarmConfig.serializer(), config)
        assertEquals(config, AppJson.decodeFromString(AlarmConfig.serializer(), text))
    }

    private fun item(spec: AlarmSpec, calendar: CalendarSnapshot = empty) = StatusItem(spec, "Trabajo", calendar)

    private val weekdaySpec = AlarmSpec(
        1,
        config = AlarmConfig(hour = 7, minute = 0, days = java.time.DayOfWeek.values().take(5).toSet()),
    )

    @Test
    fun focusIsTodayWhileAnAlarmRemainsThenTomorrow() {
        val early = monday.atTime(6, 0).atZone(zone)
        val status = DayPlanner.focus(listOf(item(weekdaySpec)), early)
        assertEquals(DayStatus(monday, DayState.RINGS, LocalTime.of(7, 0), "Trabajo", 1), status)

        val late = monday.atTime(8, 0).atZone(zone)
        val next = DayPlanner.focus(listOf(item(weekdaySpec)), late)
        assertEquals(monday.plusDays(1), next.date)
    }

    @Test
    fun skippedDayExplainsReason() {
        val rule = AlarmRule("v", conditions = listOf(DatesCondition(listOf(DateRange(monday.plusDays(1), monday.plusDays(1))))))
        val spec = weekdaySpec.copy(config = weekdaySpec.config.copy(rules = listOf(rule)))
        val status = DayPlanner.focus(listOf(item(spec)), monday.atTime(21, 0).atZone(zone))
        assertEquals(DayState.SKIPPED, status.state)
        assertEquals("Fechas excluidas", status.detail)
        assertEquals(1L, status.alarmId)
    }

    @Test
    fun earliestRingWinsOverOtherAlarms() {
        val second = weekdaySpec.copy(id = 2, config = weekdaySpec.config.copy(hour = 6))
        val status = DayPlanner.status(listOf(item(weekdaySpec), item(second)), monday.plusDays(1), monday.atTime(21, 0).atZone(zone))
        assertEquals(2L, status.alarmId)
        assertEquals(LocalTime.of(6, 0), status.time)
    }

    @Test
    fun contextAndMissingDataAreShownClearly() {
        val context = AlarmRule("c", conditions = listOf(ContextCondition(ContextKind.CHARGING)))
        val depends = weekdaySpec.copy(config = weekdaySpec.config.copy(rules = listOf(context)))
        val now = monday.atTime(21, 0).atZone(zone)
        assertEquals(DayState.DEPENDS, DayPlanner.status(listOf(item(depends)), monday.plusDays(1), now).state)

        val calendar = AlarmRule("k", conditions = listOf(CalendarCondition(setOf(5))))
        val noData = weekdaySpec.copy(config = weekdaySpec.config.copy(rules = listOf(calendar)))
        val status = DayPlanner.status(listOf(item(noData, CalendarSnapshot.Unavailable)), monday.plusDays(1), now)
        assertEquals(DayState.NO_DATA, status.state)
    }

    @Test
    fun weekShowsShiftsAndRest() {
        val now = monday.minusDays(1).atTime(5, 0).atZone(zone)
        val week = DayPlanner.week(listOf(item(shiftSpec(threeWeeks))), now)
        assertEquals(7, week.size)
        assertEquals(DayState.SKIPPED, week[0].state)
        assertEquals(LocalTime.of(6, 0), week[1].time)
        assertEquals("Mañana", week[1].detail)
    }

    @Test
    fun burstShowsNextRemainingRepeat() {
        val spec = weekdaySpec.copy(config = weekdaySpec.config.copy(burst = BurstConfig(7, 30, 10)))
        val status = DayPlanner.focus(listOf(item(spec)), monday.atTime(7, 5).atZone(zone))
        assertEquals(LocalTime.of(7, 10), status.time)
    }

    @Test
    fun noAlarmsMeansNone() {
        val status = DayPlanner.focus(emptyList(), monday.atTime(9, 0).atZone(zone))
        assertEquals(DayState.NONE, status.state)
        assertEquals(monday.plusDays(1), status.date)
    }

    @Test
    fun aPastSkippedMomentIsNotCountedAsSkipped() {
        val holiday = AlarmRule("f", conditions = listOf(DatesCondition(listOf(DateRange(monday, monday)))))
        val spec = weekdaySpec.copy(config = weekdaySpec.config.copy(rules = listOf(holiday)))
        val evening = AlarmEngine.plan(spec, monday.atTime(19, 47).atZone(zone), empty)
        assertEquals(monday.plusDays(1).atTime(7, 0).atZone(zone), evening.fireAt)
        assertTrue(evening.skipped.isEmpty())

        val dawn = AlarmEngine.plan(spec, monday.atTime(5, 0).atZone(zone), empty)
        assertEquals(1, dawn.skipped.size)
    }

    @Test
    fun oneTimeChangesOnlyThatDay() {
        val tuesday = monday.plusDays(1)
        val spec = weekdaySpec.copy(oneTime = tuesday.atTime(7, 30))
        val now = monday.atTime(21, 0).atZone(zone)
        assertEquals(tuesday.atTime(7, 30).atZone(zone), AlarmEngine.plan(spec, now, empty).fireAt)
        val after = tuesday.atTime(8, 0).atZone(zone)
        assertEquals(tuesday.plusDays(1).atTime(7, 0).atZone(zone), AlarmEngine.plan(spec, after, empty).fireAt)
        assertEquals(
            Decision.Ring(LocalTime.of(7, 30), AlarmEngine.ONE_TIME_NOTE),
            AlarmEngine.evaluate(spec, tuesday, empty),
        )
    }

    @Test
    fun oneTimeBeatsRulesButNotManualSkip() {
        val tuesday = monday.plusDays(1)
        val holiday = AlarmRule("f", conditions = listOf(DatesCondition(listOf(DateRange(tuesday, tuesday)))))
        val spec = weekdaySpec.copy(
            config = weekdaySpec.config.copy(rules = listOf(holiday)),
            oneTime = tuesday.atTime(9, 0),
        )
        assertTrue(AlarmEngine.evaluate(spec, tuesday, empty) is Decision.Ring)
        assertTrue(AlarmEngine.evaluate(spec.copy(skipDate = tuesday), tuesday, empty) is Decision.Skip)
    }

    @Test
    fun oneTimeKeepsBurstLength() {
        val tuesday = monday.plusDays(1)
        val config = weekdaySpec.config.copy(burst = BurstConfig(7, 30, 10))
        val spec = weekdaySpec.copy(config = config, oneTime = tuesday.atTime(8, 0))
        val ring = AlarmEngine.evaluate(spec, tuesday, empty) as Decision.Ring
        assertEquals(BurstConfig(8, 30, 10), ring.burst)
    }

    @Test
    fun nextScheduledDayIgnoresRules() {
        val holiday = AlarmRule("f", conditions = listOf(DatesCondition(listOf(DateRange(monday.plusDays(1), monday.plusDays(1))))))
        val spec = weekdaySpec.copy(config = weekdaySpec.config.copy(rules = listOf(holiday)))
        assertEquals(
            monday.plusDays(1) to LocalTime.of(7, 0),
            AlarmEngine.nextScheduledDay(spec, monday.atTime(21, 0).atZone(zone), empty),
        )
        val friday = monday.plusDays(4)
        assertEquals(
            monday.plusDays(7) to LocalTime.of(7, 0),
            AlarmEngine.nextScheduledDay(spec, friday.atTime(8, 0).atZone(zone), empty),
        )
        assertEquals(
            monday to LocalTime.of(7, 0),
            AlarmEngine.nextScheduledDay(spec, monday.atTime(6, 0).atZone(zone), empty),
        )
    }

    @Test
    fun shiftDayUsesShiftTimeForNextChange() {
        val now = monday.minusDays(1).atTime(20, 0).atZone(zone)
        assertEquals(monday to LocalTime.of(6, 0), AlarmEngine.nextScheduledDay(shiftSpec(threeWeeks), now, empty))
    }
}
