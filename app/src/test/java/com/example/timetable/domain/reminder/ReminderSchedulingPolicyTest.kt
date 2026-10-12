package com.example.timetable.domain.reminder

import com.example.timetable.domain.model.ReminderSchedule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulingPolicyTest {

    @Test
    fun grantedPermissionChoosesExactAlarm() {
        val dispatch = ReminderSchedulingPolicy.decide(
            schedule = schedule(enabled = true),
            nextTriggerAtMillis = TRIGGER,
            canScheduleExactAlarms = true,
        )

        assertTrue(dispatch is ReminderDispatch.Exact)
        assertEquals(TRIGGER, (dispatch as ReminderDispatch.Exact).triggerAtMillis)
        assertEquals(RULE_ID, dispatch.ruleId)
    }

    @Test
    fun deniedPermissionFallsBackToWorkManager() {
        val dispatch = ReminderSchedulingPolicy.decide(
            schedule = schedule(enabled = true),
            nextTriggerAtMillis = TRIGGER,
            canScheduleExactAlarms = false,
        )

        assertTrue(dispatch is ReminderDispatch.Inexact)
        assertEquals(TRIGGER, (dispatch as ReminderDispatch.Inexact).triggerAtMillis)
    }

    @Test
    fun disabledRuleIsSkipped() {
        val dispatch = ReminderSchedulingPolicy.decide(
            schedule = schedule(enabled = false),
            nextTriggerAtMillis = TRIGGER,
            canScheduleExactAlarms = true,
        )

        assertEquals(ReminderDispatch.Skipped(RULE_ID, SkipReason.DISABLED), dispatch)
    }

    @Test
    fun noTriggerWindowIsSkipped() {
        val dispatch = ReminderSchedulingPolicy.decide(
            schedule = schedule(enabled = true),
            nextTriggerAtMillis = null,
            canScheduleExactAlarms = true,
        )

        assertEquals(ReminderDispatch.Skipped(RULE_ID, SkipReason.NO_TRIGGER_WINDOW), dispatch)
    }

    @Test
    fun disabledBeatsGrantedPermission() {
        val dispatch = ReminderSchedulingPolicy.decide(
            schedule = schedule(enabled = false),
            nextTriggerAtMillis = TRIGGER,
            canScheduleExactAlarms = true,
        )

        assertTrue(dispatch is ReminderDispatch.Skipped)
    }

    private fun schedule(enabled: Boolean) = ReminderSchedule(
        ruleId = RULE_ID,
        courseId = 9L,
        courseName = "数据结构",
        location = "计算机楼 402",
        weekday = 1,
        startSection = 1,
        startTime = "08:00",
        timetableId = 1L,
        timetableName = "2026 秋",
        timetableStartDate = LocalDate.of(2026, 9, 7),
        advanceMinutes = 15,
        enabled = enabled,
        ruleWeeks = emptyList(),
        courseWeeks = listOf(1),
    )

    private companion object {
        const val RULE_ID = 42L
        const val TRIGGER = 1_787_000_000_000L
    }
}
