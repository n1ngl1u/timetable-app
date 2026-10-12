package com.example.timetable.domain.usecase

import com.example.timetable.testing.FakeSettingsRepository
import com.example.timetable.testing.FakeTimetableRepository
import com.example.timetable.testing.populatedTimetable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableSelectionUseCaseTest {

    private val repository = FakeTimetableRepository()

    private val settings = FakeSettingsRepository()

    private val useCase = TimetableSelectionUseCase(
        settingsRepository = settings,
        timetableRepository = repository,
    )

    @Test
    fun selectSwitchesActiveTimetable() = runTest {
        repository.upsertTimetable(populatedTimetable("第一张", courseCount = 1))
        val second = repository.upsertTimetable(populatedTimetable("第二张", courseCount = 2))

        assertTrue(useCase.select(second))

        assertEquals(second, settings.current().activeTimetableId)
    }

    @Test
    fun selectMissingTimetableKeepsPreviousActive() = runTest {
        val first = repository.upsertTimetable(populatedTimetable("第一张", courseCount = 1))
        useCase.select(first)

        assertFalse(useCase.select(999L))

        assertEquals(first, settings.current().activeTimetableId)
    }

    @Test
    fun deleteActiveTimetableMovesToFirstRemaining() = runTest {
        val first = repository.upsertTimetable(populatedTimetable("第一张", courseCount = 1))
        val second = repository.upsertTimetable(populatedTimetable("第二张", courseCount = 2))
        useCase.select(second)

        val nextActive = useCase.delete(second)

        assertEquals(first, nextActive)
        assertEquals(first, settings.current().activeTimetableId)
        assertEquals(1, repository.observeTimetables().first().size)
    }

    @Test
    fun deleteLastTimetableClearsActive() = runTest {
        val only = repository.upsertTimetable(populatedTimetable("唯一一张", courseCount = 1))
        useCase.select(only)

        val nextActive = useCase.delete(only)

        assertNull(nextActive)
        assertNull(settings.current().activeTimetableId)
        assertTrue(repository.observeTimetables().first().isEmpty())
    }

    @Test
    fun deleteInactiveTimetableKeepsActive() = runTest {
        val first = repository.upsertTimetable(populatedTimetable("第一张", courseCount = 1))
        val second = repository.upsertTimetable(populatedTimetable("第二张", courseCount = 2))
        useCase.select(second)

        val nextActive = useCase.delete(first)

        assertEquals(second, nextActive)
        assertEquals(second, settings.current().activeTimetableId)
    }
}
