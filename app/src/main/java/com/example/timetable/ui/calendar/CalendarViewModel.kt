package com.example.timetable.ui.calendar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.timetable.core.time.TimeProvider
import com.example.timetable.core.ui.BaseViewModel
import com.example.timetable.core.ui.UiState
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.usecase.ObserveActiveTimetableUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val observeActiveTimetable: ObserveActiveTimetableUseCase,
    private val timeProvider: TimeProvider,
    private val savedStateHandle: SavedStateHandle,
) : BaseViewModel() {

    private val today: LocalDate = timeProvider.today()

    /**
     * 月份与选中日期都放 SavedStateHandle：ViewModel 是这两份状态的真源，
     * 拆一半给 Composable 的 rememberSaveable 会变成两个真源；
     * SavedStateHandle 同样扛进程重建，且重建后 Sheet 的课程列表能一起恢复。
     */
    private val mutableMonth = MutableStateFlow(restoreMonth())

    private val mutableSelectedDate = MutableStateFlow(restoreSelectedDate())

    private val mutableState = MutableStateFlow<UiState<CalendarUiModel>>(UiState.Loading)

    val state: StateFlow<UiState<CalendarUiModel>> = mutableState.asStateFlow()

    val initialMonth: YearMonth get() = restoreMonth()

    init {
        viewModelScope.launch {
            combine(
                observeActiveTimetable.activeTimetable,
                mutableMonth,
                mutableSelectedDate,
            ) { timetable, month, selectedDate ->
                buildModel(timetable, month, selectedDate)
            }.collect { model -> mutableState.value = UiState.Success(model) }
        }
    }

    fun showMonth(month: YearMonth) {
        if (month == mutableMonth.value) return
        mutableMonth.value = month
        savedStateHandle[KEY_MONTH] = month.toString()
    }

    fun selectDate(date: LocalDate) {
        mutableSelectedDate.value = date
        savedStateHandle[KEY_SELECTED_DATE] = date.toString()
    }

    fun dismissDayDetail() {
        mutableSelectedDate.value = null
        savedStateHandle[KEY_SELECTED_DATE] = null
    }

    private fun buildModel(
        timetable: Timetable?,
        month: YearMonth,
        selectedDate: LocalDate?,
    ): CalendarUiModel {
        val index = timetable?.toIndex()
        return CalendarUiModel(
            month = month,
            index = index,
            today = today,
            selectedDate = selectedDate,
            selectedDayCourses = selectedDate?.let { date ->
                index?.coursesOn(date).orEmpty().map { course ->
                    DayCourseItem(
                        courseId = course.id,
                        name = course.name,
                        location = course.location,
                        timeText = index!!.timeTextOf(course),
                    )
                }
            }.orEmpty(),
        )
    }

    /** 复用活跃课表做内存投影：课程量级 ≤ 50，不新增日期范围查询。 */
    private fun Timetable.toIndex(): CourseScheduleIndex = CourseScheduleIndex(
        startDate = startDate,
        timeSlots = timeSlots.associate { slot -> slot.number to (slot.startTime to slot.endTime) },
        byWeekday = courses.groupBy(Course::weekday).mapValues { (_, list) ->
            list.map { course ->
                ScheduledCourse(
                    id = course.id,
                    name = course.name,
                    location = course.location,
                    startSection = course.startSection,
                    endSection = course.endSection,
                    weeks = course.weeks,
                )
            }
        },
    )

    private fun restoreMonth(): YearMonth {
        val stored = savedStateHandle.get<String>(KEY_MONTH)
        return stored?.let { value -> runCatching { YearMonth.parse(value) }.getOrNull() }
            ?: YearMonth.from(today)
    }

    private fun restoreSelectedDate(): LocalDate? {
        val stored = savedStateHandle.get<String>(KEY_SELECTED_DATE) ?: return null
        return runCatching { LocalDate.parse(stored) }.getOrNull()
    }

    private companion object {
        const val KEY_MONTH = "year_month"
        const val KEY_SELECTED_DATE = "selected_date"
    }
}
