package com.example.timetable.widget

import com.example.timetable.domain.usecase.ObserveActiveTimetableUseCase
import com.example.timetable.domain.usecase.TimetableSelectionUseCase
import com.example.timetable.testing.FakeDispatcherProvider
import com.example.timetable.testing.FakeSettingsRepository
import com.example.timetable.testing.FakeTimetableRepository
import com.example.timetable.testing.populatedTimetable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「小组件跟随活跃课表」的直接验证：订阅 WidgetDataObserver 的合并流，
 * 切换活跃课表后断言发射出新的课程数——不靠推断。
 */
class WidgetDataObserverTest {

    private val repository = FakeTimetableRepository()

    private val settings = FakeSettingsRepository()

    private val observer = WidgetDataObserver(
        timetableRepository = repository,
        settingsRepository = settings,
        observeActiveTimetable = ObserveActiveTimetableUseCase(settings, repository),
        dispatchers = FakeDispatcherProvider(),
    )

    @Test
    fun switchingActiveTimetableEmitsNewCourseCount() = runTest {
        val first = repository.upsertTimetable(populatedTimetable("第一张", courseCount = 1))
        val second = repository.upsertTimetable(populatedTimetable("第二张", courseCount = 3))
        settings.setActiveTimetable(first)

        val seen = mutableListOf<WidgetRevision>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.revisions.collect { revision -> seen.add(revision) }
        }
        runCurrent()

        assertTrue(
            "初始状态未发射活跃课表数据: " + seen,
            seen.any { it.activeTimetableId == first && it.courseCount == 1 },
        )

        TimetableSelectionUseCase(settings, repository).select(second)
        runCurrent()

        assertTrue(
            "切换活跃课表未发射新的课程数: " + seen,
            seen.any { it.activeTimetableId == second && it.courseCount == 3 },
        )
    }

    @Test
    fun clearingActiveTimetableEmitsZeroCount() = runTest {
        val only = repository.upsertTimetable(populatedTimetable("唯一一张", courseCount = 2))
        settings.setActiveTimetable(only)

        val seen = mutableListOf<WidgetRevision>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.revisions.collect { revision -> seen.add(revision) }
        }
        runCurrent()

        settings.setActiveTimetable(null)
        runCurrent()

        assertTrue(
            "清空活跃课表未发射 0 课程数: " + seen,
            seen.any { it.activeTimetableId == null && it.courseCount == 0 },
        )
    }

    @Test
    fun importedTimetableEmitsRevision() = runTest {
        val seen = mutableListOf<WidgetRevision>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observer.revisions.collect { revision -> seen.add(revision) }
        }
        runCurrent()

        val id = repository.upsertTimetable(populatedTimetable("新导入", courseCount = 4))
        settings.setActiveTimetable(id)
        runCurrent()

        assertTrue(
            "导入后未发射课表张数与课程数: " + seen,
            seen.any { it.timetableCount == 1 && it.courseCount == 4 },
        )
    }
}
