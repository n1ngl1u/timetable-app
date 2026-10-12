package com.example.timetable.domain.usecase

import com.example.timetable.testing.FakeSettingsRepository
import com.example.timetable.testing.FakeTimetableRepository
import com.example.timetable.testing.populatedTimetable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObserveActiveTimetableUseCaseTest {

    private val repository = FakeTimetableRepository()

    private val settings = FakeSettingsRepository()

    private val useCase = ObserveActiveTimetableUseCase(
        settingsRepository = settings,
        timetableRepository = repository,
    )

    @Test
    fun noActiveTimetableYieldsEmptyWithoutThrowing() = runTest {
        repository.upsertTimetable(populatedTimetable("有一张但不活跃", courseCount = 2))

        assertNull(useCase.activeTimetable.first())
        assertEquals(0, useCase.activeCourseCount.first())
        assertNull(useCase.activeTimetableOnce())
    }

    @Test
    fun activeTimetableAndCountFollowSelection() = runTest {
        val first = repository.upsertTimetable(populatedTimetable("第一张", courseCount = 2))
        val second = repository.upsertTimetable(populatedTimetable("第二张", courseCount = 5))

        settings.setActiveTimetable(second)
        assertEquals("第二张", useCase.activeTimetable.first()?.tableName)
        assertEquals(5, useCase.activeCourseCount.first())

        settings.setActiveTimetable(first)
        assertEquals("第一张", useCase.activeTimetable.first()?.tableName)
        assertEquals(2, useCase.activeCourseCount.first())
    }

    @Test
    fun unknownActiveIdYieldsEmpty() = runTest {
        repository.upsertTimetable(populatedTimetable("唯一一张", courseCount = 3))
        settings.setActiveTimetable(999L)

        assertNull(useCase.activeTimetable.first())
        assertEquals(0, useCase.activeCourseCount.first())
    }

    @Test
    fun clearingActiveTimetableYieldsEmpty() = runTest {
        val id = repository.upsertTimetable(populatedTimetable("唯一一张", courseCount = 3))
        settings.setActiveTimetable(id)
        assertEquals(3, useCase.activeCourseCount.first())

        settings.setActiveTimetable(null)
        assertEquals(0, useCase.activeCourseCount.first())
        assertNull(useCase.activeTimetable.first())
    }

    @Test
    fun activeTimetableOnceReturnsFullAggregate() = runTest {
        val id = repository.upsertTimetable(populatedTimetable("完整课表", courseCount = 4))
        settings.setActiveTimetable(id)

        val timetable = useCase.activeTimetableOnce()

        assertEquals("完整课表", timetable?.tableName)
        assertEquals(4, timetable?.courses?.size)
    }
}
