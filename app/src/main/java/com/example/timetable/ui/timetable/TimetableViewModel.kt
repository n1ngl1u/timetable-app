package com.example.timetable.ui.timetable

import androidx.lifecycle.viewModelScope
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import com.example.timetable.core.ui.BaseViewModel
import com.example.timetable.core.ui.UiEvent
import com.example.timetable.core.ui.UiState
import com.example.timetable.data.adapter.local.LocalFileSchoolAdapter
import com.example.timetable.data.reminder.ReminderPermissionChecker
import com.example.timetable.data.reminder.ReminderScheduler
import com.example.timetable.domain.importer.ImportRequest
import com.example.timetable.domain.importer.ImportResult
import com.example.timetable.domain.model.AppSettings
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.ReminderRule
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import com.example.timetable.domain.usecase.DeleteCourseResult
import com.example.timetable.domain.usecase.DeleteCourseUseCase
import com.example.timetable.domain.usecase.ImportTimetableUseCase
import com.example.timetable.domain.usecase.ObserveActiveTimetableUseCase
import com.example.timetable.domain.usecase.SyncReminderRulesUseCase
import com.example.timetable.domain.usecase.TimetableSelectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** UI 投影类型：只把渲染需要的字段带进状态。 */
data class TimetableOption(
    val id: Long,
    val name: String,
)

data class CourseRow(
    val id: Long,
    val name: String,
    val location: String,
    val weekdayLabel: String,
    val sectionText: String,
)

data class TimetableUiModel(
    /** 预留：纯净模式专属视觉接入点，当前仅 AppNavHost 层响应。 */
    val pureModeEnabled: Boolean,
    val activeTimetableId: Long?,
    val activeTimetableName: String?,
    val courseCount: Int,
    val timetables: List<TimetableOption>,
    val remindersEnabled: Boolean,
    val reminderAdvanceMinutes: Int,
    val advanceOptions: List<Int>,
    val exactAlarmsAllowed: Boolean,
    val notificationsAllowed: Boolean,
    val courses: List<CourseRow>,
    val pendingDelete: CourseRow?,
) {
    val showNotificationDisabledHint: Boolean get() = remindersEnabled && !notificationsAllowed

    val showExactAlarmDisabledHint: Boolean get() = remindersEnabled && !exactAlarmsAllowed
}

private data class PermissionUiState(
    val exactAlarmsAllowed: Boolean,
    val notificationsAllowed: Boolean,
)

private data class HeaderState(
    val settings: AppSettings,
    val courseCount: Int,
    val timetables: List<Timetable>,
    val permissions: PermissionUiState,
)

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val timetableRepository: TimetableRepository,
    private val observeActiveTimetable: ObserveActiveTimetableUseCase,
    private val timetableSelection: TimetableSelectionUseCase,
    private val syncReminderRules: SyncReminderRulesUseCase,
    private val reminderScheduler: ReminderScheduler,
    private val reminderPermissionChecker: ReminderPermissionChecker,
    private val importTimetableUseCase: ImportTimetableUseCase,
    private val deleteCourseUseCase: DeleteCourseUseCase,
    private val perfTracer: PerfTracer,
) : BaseViewModel() {

    private val mutableState = MutableStateFlow<UiState<TimetableUiModel>>(UiState.Loading)

    val state: StateFlow<UiState<TimetableUiModel>> = mutableState.asStateFlow()

    private val permissionState = MutableStateFlow(
        PermissionUiState(exactAlarmsAllowed = false, notificationsAllowed = false),
    )

    private val pendingDelete = MutableStateFlow<CourseRow?>(null)

    init {
        viewModelScope.launch {
            val header = combine(
                settingsRepository.settings,
                observeActiveTimetable.activeCourseCount,
                timetableRepository.observeTimetables(),
                permissionState,
            ) { settings, courseCount, timetables, permissions ->
                HeaderState(settings, courseCount, timetables, permissions)
            }
            combine(
                header,
                observeActiveTimetable.activeTimetable,
                pendingDelete,
            ) { headerState, timetable, pending ->
                buildModel(headerState, timetable, pending)
            }.collect { model -> mutableState.value = UiState.Success(model) }
        }
        refreshPermissionState()
    }

    fun onTimetableSelected(timetableId: Long) {
        viewModelScope.launch {
            if (!timetableSelection.select(timetableId)) {
                sendEvent(UiEvent.ShowMessage("课表不存在"))
                return@launch
            }
            syncReminderRules.applyToActiveTimetable()
            reminderScheduler.rescheduleAll()
        }
    }

    fun onRemindersEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(remindersEnabled = enabled) }
            syncReminderRules.applyToActiveTimetable()
            reminderScheduler.rescheduleAll()
            refreshPermissionState()
            if (!enabled) return@launch
            if (!reminderScheduler.canScheduleExactAlarms()) {
                sendEvent(
                    UiEvent.OpenSystemSettings(ReminderPermissionChecker.ACTION_REQUEST_EXACT_ALARM),
                )
            }
            if (!reminderPermissionChecker.notificationsAllowed()) {
                sendEvent(UiEvent.RequestNotificationPermission)
            }
        }
    }

    fun onReminderAdvanceChange(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(defaultReminderAdvanceMinutes = minutes) }
            syncReminderRules.applyToActiveTimetable()
            reminderScheduler.rescheduleAll()
        }
    }

    fun onPermissionStateMaybeChanged() {
        refreshPermissionState()
    }

    fun onCourseLongPressed(course: CourseRow) {
        pendingDelete.value = course
    }

    fun onDeleteDismissed() {
        pendingDelete.value = null
    }

    /** 删除编排全部交给 DeleteCourseUseCase：先删系统日历事件，再删课程。 */
    fun onDeleteConfirmed() {
        val target = pendingDelete.value ?: return
        viewModelScope.launch {
            when (val result = deleteCourseUseCase(target.id)) {
                is DeleteCourseResult.Deleted -> {
                    pendingDelete.value = null
                    sendEvent(UiEvent.ShowMessage("已删除：" + target.name))
                }
                is DeleteCourseResult.DeletedWithResidualCalendarEvents -> {
                    pendingDelete.value = null
                    sendEvent(UiEvent.ShowMessage("日历权限已撤销，系统日历中可能残留旧事件，恢复权限后同步会自动清理"))
                }
                is DeleteCourseResult.AbortedByCalendar -> {
                    pendingDelete.value = null
                    sendEvent(UiEvent.ShowMessage("系统日历事件删除失败，已取消删除：" + result.message))
                }
            }
        }
    }

    private fun refreshPermissionState() {
        permissionState.value = PermissionUiState(
            exactAlarmsAllowed = reminderScheduler.canScheduleExactAlarms(),
            notificationsAllowed = reminderPermissionChecker.notificationsAllowed(),
        )
    }

    private fun buildModel(
        header: HeaderState,
        timetable: Timetable?,
        pending: CourseRow?,
    ): TimetableUiModel {
        val options = header.timetables.map { TimetableOption(id = it.id, name = it.tableName) }
        return TimetableUiModel(
            pureModeEnabled = header.settings.pureModeEnabled,
            activeTimetableId = header.settings.activeTimetableId,
            activeTimetableName = options
                .firstOrNull { it.id == header.settings.activeTimetableId }
                ?.name,
            courseCount = header.courseCount,
            timetables = options,
            remindersEnabled = header.settings.remindersEnabled,
            reminderAdvanceMinutes = header.settings.defaultReminderAdvanceMinutes,
            advanceOptions = ReminderRule.ADVANCE_OPTIONS,
            exactAlarmsAllowed = header.permissions.exactAlarmsAllowed,
            notificationsAllowed = header.permissions.notificationsAllowed,
            courses = timetable?.courses.orEmpty().map { it.toRow(timetable) },
            pendingDelete = pending,
        )
    }

    private fun Course.toRow(timetable: Timetable?): CourseRow = CourseRow(
        id = id,
        name = name,
        location = location,
        weekdayLabel = WEEKDAY_LABELS.getOrElse(weekday - 1) { "" },
        sectionText = sectionTextOf(timetable, this),
    )

    private fun sectionTextOf(timetable: Timetable?, course: Course): String {
        val start = timetable?.timeSlots?.firstOrNull { it.number == course.startSection }?.startTime
        val end = timetable?.timeSlots?.firstOrNull { it.number == course.endSection }?.endTime
        return if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
            start + "-" + end
        } else if (course.startSection == course.endSection) {
            "第 " + course.startSection + " 节"
        } else {
            "第 " + course.startSection + "-" + course.endSection + " 节"
        }
    }

    /**
     * 导入入口只服务基准测试与最小接线：T0 在此打点，T1 在课表数据落库后
     * 首次被数据流观察到时收口；失败时不收口，基准测试会因「未采集到打点」直接失败。
     */
    fun importPayload(payload: String) {
        viewModelScope.launch {
            perfTracer.mark(TraceTags.IMPORT_FIRST_FRAME)
            val result = importTimetableUseCase(
                ImportRequest(
                    schoolId = LocalFileSchoolAdapter.SCHOOL_ID,
                    payload = payload,
                ),
            )
            when (result) {
                is ImportResult.Success -> {
                    timetableRepository.observeCourseCount(result.timetableId).first { it > 0 }
                    perfTracer.end(TraceTags.IMPORT_FIRST_FRAME)
                    sendEvent(UiEvent.ShowMessage("导入成功：" + result.courseCount + " 门课程"))
                }
                is ImportResult.SessionExpired -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.NetworkFailure -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.SchemaChanged -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.MissingField -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.InvalidInput -> sendEvent(UiEvent.ShowMessage(result.message))
            }
        }
    }

    private companion object {
        val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}
