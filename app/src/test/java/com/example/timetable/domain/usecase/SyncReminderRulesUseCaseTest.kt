package com.example.timetable.domain.usecase

import com.example.timetable.testing.FakeReminderRepository
import com.example.timetable.testing.FakeSettingsRepository
import com.example.timetable.testing.FakeTimetableRepository
import com.example.timetable.testing.populatedTimetable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncReminderRulesUseCaseTest {

    private val reminders = FakeReminderRepository()

    private val timetables = FakeTimetableRepository()

    private val settings = FakeSettingsRepository()

    private val useCase = SyncReminderRulesUseCase(
        reminderRepository = reminders,
        timetableRepository = timetables,
        settingsRepository = settings,
    )

    @Test
    fun noActiveTimetableAppliesNothing() = runTest {
        assertEquals(0, useCase.applyToActiveTimetable())
        assertTrue(reminders.observeRules().first().isEmpty())
    }

    @Test
    fun enablingCreatesOneRulePerCourseWithDefaultAdvance() = runTest {
        val id = timetables.upsertTimetable(populatedTimetable("2026 秋", courseCount = 3))
        settings.setActiveTimetable(id)
        settings.update { it.copy(remindersEnabled = true, defaultReminderAdvanceMinutes = 30) }

        val enabledCount = useCase.applyToActiveTimetable()

        assertEquals(3, enabledCount)
        val rules = reminders.observeRules().first()
        assertEquals(3, rules.size)
        assertTrue(rules.all { it.enabled })
        assertTrue(rules.all { it.advanceMinutes == 30 })
    }

    @Test
    fun disablingMarksExistingRulesDisabled() = runTest {
        val id = timetables.upsertTimetable(populatedTimetable("2026 秋", courseCount = 2))
        settings.setActiveTimetable(id)
        settings.update { it.copy(remindersEnabled = true) }
        useCase.applyToActiveTimetable()

        settings.update { it.copy(remindersEnabled = false) }
        val enabledCount = useCase.applyToActiveTimetable()

        assertEquals(0, enabledCount)
        val rules = reminders.observeRules().first()
        assertEquals("规则数量不变，只翻转开关", 2, rules.size)
        assertTrue(rules.none { it.enabled })
    }

    @Test
    fun reapplyingDoesNotDuplicateRules() = runTest {
        val id = timetables.upsertTimetable(populatedTimetable("2026 秋", courseCount = 4))
        settings.setActiveTimetable(id)
        settings.update { it.copy(remindersEnabled = true) }

        useCase.applyToActiveTimetable()
        useCase.applyToActiveTimetable()
        useCase.applyToActiveTimetable()

        assertEquals(4, reminders.observeRules().first().size)
    }

    @Test
    fun advanceChangeUpdatesExistingRulesInPlace() = runTest {
        val id = timetables.upsertTimetable(populatedTimetable("2026 秋", courseCount = 2))
        settings.setActiveTimetable(id)
        settings.update { it.copy(remindersEnabled = true, defaultReminderAdvanceMinutes = 15) }
        useCase.applyToActiveTimetable()

        settings.update { it.copy(defaultReminderAdvanceMinutes = 60) }
        useCase.applyToActiveTimetable()

        val rules = reminders.observeRules().first()
        assertEquals(2, rules.size)
        assertTrue(rules.all { it.advanceMinutes == 60 })
    }
}
