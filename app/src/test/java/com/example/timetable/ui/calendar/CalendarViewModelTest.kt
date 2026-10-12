package com.example.timetable.ui.calendar

import androidx.lifecycle.SavedStateHandle
import com.example.timetable.core.ui.UiState
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.TimeSlot
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.usecase.ObserveActiveTimetableUseCase
import com.example.timetable.testing.FakeSettingsRepository
import com.example.timetable.testing.FakeTimeProvider
import com.example.timetable.testing.FakeTimetableRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val monday = LocalDate.of(2026, 9, 7)

    private val timetables = FakeTimetableRepository()

    private val settings = FakeSettingsRepository()

    private val timeProvider = FakeTimeProvider(today = monday)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = CalendarViewModel(
        observeActiveTimetable = ObserveActiveTimetableUseCase(settings, timetables),
        timeProvider = timeProvider,
        savedStateHandle = handle,
    )

    private fun model(vm: CalendarViewModel): CalendarUiModel =
        (vm.state.value as UiState.Success<CalendarUiModel>).data

    @Test
    fun emptyTimetableStillRendersMonthGrid() = runTest {
        val vm = viewModel()

        val model = model(vm)
        assertEquals(YearMonth.of(2026, 9), model.month)
        assertTrue(MonthGrid.weeksOf(model.month).isNotEmpty())
        assertNull("无课表时不应有排课投影", model.index)
        assertNull(model.selectedDate)
    }

    @Test
    fun courseCountsAppearOnlyInEffectiveWeeks() = runTest {
        val id = timetables.upsertTimetable(
            timetable(
                courses = listOf(
                    Course(
                        id = 1L,
                        name = "数据结构",
                        weekday = 1,
                        startSection = 1,
                        endSection = 2,
                        weeks = listOf(1),
                    ),
                ),
            ),
        )
        settings.setActiveTimetable(id)

        val model = model(viewModel())
        val counts = model.index!!.countsIn(model.month)

        assertEquals(1, counts[monday])
        assertEquals("第 2 周不在 weeks 里，不应计课", 0, counts[monday.plusWeeks(1)])
    }

    @Test
    fun selectDateFillsDayDetailList() = runTest {
        val id = timetables.upsertTimetable(
            timetable(
                courses = listOf(
                    Course(
                        id = 7L,
                        name = "高等数学",
                        location = "教一 101",
                        weekday = 1,
                        startSection = 1,
                        endSection = 2,
                        weeks = listOf(1),
                    ),
                ),
            ),
        )
        settings.setActiveTimetable(id)
        val vm = viewModel()

        vm.selectDate(monday)

        val selected = model(vm)
        assertEquals(monday, selected.selectedDate)
        assertEquals(1, selected.selectedDayCourses.size)
        assertEquals("高等数学", selected.selectedDayCourses.single().name)
        assertEquals("08:00-09:40", selected.selectedDayCourses.single().timeText)
    }

    @Test
    fun dismissClearsSelection() = runTest {
        val vm = viewModel()
        vm.selectDate(monday)

        vm.dismissDayDetail()

        val model = model(vm)
        assertNull(model.selectedDate)
        assertTrue(model.selectedDayCourses.isEmpty())
    }

    @Test
    fun selectedDateOutsideSemesterHasNoCourses() = runTest {
        val id = timetables.upsertTimetable(timetable(courses = listOf(course(weekday = 1))))
        settings.setActiveTimetable(id)
        val vm = viewModel()

        vm.selectDate(LocalDate.of(2026, 6, 1))

        assertTrue(model(vm).selectedDayCourses.isEmpty())
    }

    @Test
    fun monthIsRestoredFromSavedStateHandle() = runTest {
        val handle = SavedStateHandle(mapOf("year_month" to "2027-03"))

        assertEquals(YearMonth.of(2027, 3), model(viewModel(handle)).month)
    }

    @Test
    fun selectedDateIsRestoredFromSavedStateHandle() = runTest {
        val handle = SavedStateHandle(mapOf("selected_date" to "2026-09-07"))

        val model = model(viewModel(handle))

        assertEquals(monday, model.selectedDate)
    }

    @Test
    fun malformedSavedStateFallsBackToToday() = runTest {
        val handle = SavedStateHandle(mapOf("year_month" to "not-a-month", "selected_date" to "xx"))

        val model = model(viewModel(handle))

        assertEquals(YearMonth.from(monday), model.month)
        assertNull(model.selectedDate)
    }

    @Test
    fun showMonthWritesThroughToSavedStateHandle() = runTest {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)

        vm.showMonth(YearMonth.of(2027, 1))

        assertEquals("2027-01", handle.get<String>("year_month"))
        assertEquals(YearMonth.of(2027, 1), model(vm).month)
    }

    private fun timetable(courses: List<Course>) = Timetable(
        tableName = "2026 秋",
        startDate = monday,
        timeSlots = listOf(
            TimeSlot(number = 1, startTime = "08:00", endTime = "08:45"),
            TimeSlot(number = 2, startTime = "08:55", endTime = "09:40"),
        ),
        courses = courses,
    )

    private fun course(weekday: Int) = Course(
        id = 1L,
        name = "数据结构",
        weekday = weekday,
        startSection = 1,
        endSection = 2,
        weeks = listOf(1),
    )
}
