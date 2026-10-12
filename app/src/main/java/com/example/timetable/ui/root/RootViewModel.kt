package com.example.timetable.ui.root

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
import com.example.timetable.domain.model.ReminderRule
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
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

/** UI 投影类型：只把渲染需要的字段带进状态，不把整个 domain Timetable 塞进来。 */
data class TimetableOption(
    val id: Long,
    val name: String,
)

/** 首页状态：摘要一行 + 提醒开关/提前量一行。 */
data class RootUiModel(
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
) {
    val showNotificationDisabledHint: Boolean
        get() = remindersEnabled && !notificationsAllowed

    val showExactAlarmDisabledHint: Boolean
        get() = remindersEnabled && !exactAlarmsAllowed
}

private data class PermissionUiState(
    val exactAlarmsAllowed: Boolean,
    val notificationsAllowed: Boolean,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val timetableRepository: TimetableRepository,
    private val observeActiveTimetable: ObserveActiveTimetableUseCase,
    private val timetableSelection: TimetableSelectionUseCase,
    private val syncReminderRules: SyncReminderRulesUseCase,
    private val reminderScheduler: ReminderScheduler,
    private val reminderPermissionChecker: ReminderPermissionChecker,
    private val importTimetableUseCase: ImportTimetableUseCase,
    private val perfTracer: PerfTracer,
) : BaseViewModel() {

    private val mutableState = MutableStateFlow<UiState<RootUiModel>>(UiState.Loading)

    val state: StateFlow<UiState<RootUiModel>> = mutableState.asStateFlow()

    private val permissionState = MutableStateFlow(
        PermissionUiState(exactAlarmsAllowed = false, notificationsAllowed = false),
    )

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                observeActiveTimetable.activeCourseCount,
                timetableRepository.observeTimetables(),
                permissionState,
            ) { settings, courseCount, timetables, permissions ->
                val options = timetables.map { TimetableOption(id = it.id, name = it.tableName) }
                RootUiModel(
                    pureModeEnabled = settings.pureModeEnabled,
                    activeTimetableId = settings.activeTimetableId,
                    activeTimetableName = options
                        .firstOrNull { it.id == settings.activeTimetableId }
                        ?.name,
                    courseCount = courseCount,
                    timetables = options,
                    remindersEnabled = settings.remindersEnabled,
                    reminderAdvanceMinutes = settings.defaultReminderAdvanceMinutes,
                    advanceOptions = ReminderRule.ADVANCE_OPTIONS,
                    exactAlarmsAllowed = permissions.exactAlarmsAllowed,
                    notificationsAllowed = permissions.notificationsAllowed,
                )
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

    /** 用户在系统权限弹窗上做出选择后，刷新提示状态（开关本身仍保持打开）。 */
    fun onPermissionStateMaybeChanged() {
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        permissionState.value = PermissionUiState(
            exactAlarmsAllowed = reminderScheduler.canScheduleExactAlarms(),
            notificationsAllowed = reminderPermissionChecker.notificationsAllowed(),
        )
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
}
