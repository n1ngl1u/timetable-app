package com.example.timetable.ui.timetable

import androidx.lifecycle.viewModelScope
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import com.example.timetable.core.ui.BaseViewModel
import com.example.timetable.core.ui.UiEvent
import com.example.timetable.core.ui.UiState
import com.example.timetable.data.adapter.local.ClipboardPayloadSource
import com.example.timetable.data.adapter.local.LocalFileSchoolAdapter
import com.example.timetable.data.reminder.ReminderPermissionChecker
import com.example.timetable.data.reminder.ReminderScheduler
import com.example.timetable.domain.calendar.CalendarSyncResult
import com.example.timetable.domain.importer.ImportRequest
import com.example.timetable.domain.importer.TimetableFetchRequest
import com.example.timetable.domain.importer.ImportResult
import com.example.timetable.domain.model.AppSettings
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.ReminderRule
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import com.example.timetable.domain.usecase.DeleteCourseResult
import com.example.timetable.domain.usecase.DeleteCourseUseCase
import com.example.timetable.domain.usecase.ImportAndSyncUseCase
import com.example.timetable.domain.usecase.ImportOutcome
import com.example.timetable.domain.usecase.ImportPreparation
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
    val importSheetVisible: Boolean,
    val importing: Boolean,
    val manualPayload: String,
    val overwritePrompt: OverwritePrompt?,
) {
    val showNotificationDisabledHint: Boolean get() = remindersEnabled && !notificationsAllowed

    val showExactAlarmDisabledHint: Boolean get() = remindersEnabled && !exactAlarmsAllowed
}

/** 覆盖确认弹窗所需的两个计数；待执行的请求本身留在 ViewModel 私有字段里，不进 UI 模型。 */
data class OverwritePrompt(
    val existingCourseCount: Int,
    val newCourseCount: Int,
)

private data class ImportUiState(
    val sheetVisible: Boolean = false,
    val importing: Boolean = false,
    val manualPayload: String = "",
    val overwrite: OverwritePrompt? = null,
)

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
    private val importAndSyncUseCase: ImportAndSyncUseCase,
    private val clipboardPayloadSource: ClipboardPayloadSource,
    private val deleteCourseUseCase: DeleteCourseUseCase,
    private val perfTracer: PerfTracer,
) : BaseViewModel() {

    private val mutableState = MutableStateFlow<UiState<TimetableUiModel>>(UiState.Loading)

    val state: StateFlow<UiState<TimetableUiModel>> = mutableState.asStateFlow()

    private val permissionState = MutableStateFlow(
        PermissionUiState(exactAlarmsAllowed = false, notificationsAllowed = false),
    )

    private val pendingDelete = MutableStateFlow<CourseRow?>(null)

    private val importState = MutableStateFlow(ImportUiState())

    /** 待覆盖确认的导入请求，只在 ViewModel 内流转。 */
    private var pendingOverwriteRequest: ImportRequest? = null

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
                importState,
            ) { headerState, timetable, pending, importUi ->
                buildModel(headerState, timetable, pending, importUi)
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
        importUi: ImportUiState,
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
            importSheetVisible = importUi.sheetVisible,
            importing = importUi.importing,
            manualPayload = importUi.manualPayload,
            overwritePrompt = importUi.overwrite,
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

    fun onImportSheetOpened() {
        importState.value = importState.value.copy(sheetVisible = true)
    }

    /** 关闭面板时清空手动粘贴内容，避免下次打开还留着上一次的文本。 */
    fun onImportSheetClosed() {
        importState.value = ImportUiState()
        pendingOverwriteRequest = null
    }

    fun onManualPayloadChanged(text: String) {
        importState.value = importState.value.copy(manualPayload = text)
    }

    fun onFilePicked(uri: String) {
        startImport(
            ImportRequest(
                schoolId = LocalFileSchoolAdapter.SCHOOL_ID,
                fetchRequest = TimetableFetchRequest(sourceUri = uri),
            ),
        )
    }

    fun onImportFromClipboard() {
        val text = runCatching { clipboardPayloadSource.readText() }.getOrElse {
            importState.value = importState.value.copy(sheetVisible = false)
            sendEvent(UiEvent.ShowMessage("剪贴板读取失败"))
            return
        }
        if (text.isNullOrBlank()) {
            importState.value = importState.value.copy(sheetVisible = false)
            sendEvent(UiEvent.ShowMessage("剪贴板里没有可用文本"))
            return
        }
        startImport(ImportRequest(schoolId = LocalFileSchoolAdapter.SCHOOL_ID, payload = text))
    }

    fun onManualImportSubmitted() {
        val text = importState.value.manualPayload
        if (text.isBlank()) {
            sendEvent(UiEvent.ShowMessage("请先粘贴课表 JSON 文本"))
            return
        }
        startImport(ImportRequest(schoolId = LocalFileSchoolAdapter.SCHOOL_ID, payload = text))
    }

    fun onOverwriteConfirmed() {
        val request = pendingOverwriteRequest ?: return
        pendingOverwriteRequest = null
        commitImport(request)
    }

    fun onOverwriteDismissed() {
        pendingOverwriteRequest = null
        importState.value = importState.value.copy(overwrite = null)
    }

    /**
     * 先准备载荷：拿到并且能解析出课程数时，如果当前有活跃课表就停下来问一次 ——
     * 整表替换不可撤销，不确认会让用户一次误触丢掉整张课表。
     * 解析失败不在这里拦，直接交给导入链路产出准确的错误原因。
     */
    private fun startImport(request: ImportRequest) {
        viewModelScope.launch {
            importState.value = ImportUiState()
            when (val preparation = importAndSyncUseCase.prepare(request)) {
                is ImportPreparation.Failure -> sendEvent(UiEvent.ShowMessage(preparation.message))
                is ImportPreparation.Unparsable -> commitImport(preparation.request)
                is ImportPreparation.Prepared -> {
                    val current = mutableState.value.let { it as? UiState.Success<TimetableUiModel> }
                        ?.data
                    val activeId = current?.activeTimetableId
                    if (activeId == null) {
                        commitImport(preparation.request)
                    } else {
                        pendingOverwriteRequest = preparation.request
                        importState.value = importState.value.copy(
                            overwrite = OverwritePrompt(
                                existingCourseCount = current.courseCount,
                                newCourseCount = preparation.newCourseCount,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun commitImport(request: ImportRequest) {
        viewModelScope.launch {
            importState.value = importState.value.copy(importing = true, sheetVisible = false)
            when (val outcome = importAndSyncUseCase.commit(request)) {
                is ImportOutcome.Success -> {
                    sendEvent(UiEvent.ShowMessage("导入成功：" + outcome.courseCount + " 门课程"))
                    val sync = outcome.calendarSync
                    if (sync is CalendarSyncResult.Failed) {
                        sendEvent(
                            UiEvent.ShowMessage("导入成功，但系统日历同步失败：" + sync.message),
                        )
                    }
                }
                is ImportOutcome.Failure -> sendEvent(UiEvent.ShowMessage("导入失败：" + outcome.message))
            }
            importState.value = ImportUiState()
            pendingOverwriteRequest = null
        }
    }

    /**
     * 导入入口只服务基准测试与最小接线：T0 在此打点，T1 在课表数据落库后
     * 首次被数据流观察到时收口；失败时不收口，基准测试会因「未采集到打点」直接失败。
     */
    fun importPayload(payload: String) {
        viewModelScope.launch {
            perfTracer.mark(TraceTags.IMPORT_FIRST_FRAME)
            val outcome = importAndSyncUseCase.commit(
                request = ImportRequest(
                    schoolId = LocalFileSchoolAdapter.SCHOOL_ID,
                    payload = payload,
                ),
                // 基准链路不做日历写入：否则 IMPORT_FIRST_FRAME 的 end 会落在同步之后，
                // 指标会被一个与导入无关的耗时污染
                syncCalendar = false,
            )
            when (outcome) {
                is ImportOutcome.Success -> {
                    timetableRepository.observeCourseCount(outcome.timetableId).first { it > 0 }
                    perfTracer.end(TraceTags.IMPORT_FIRST_FRAME)
                    sendEvent(UiEvent.ShowMessage("导入成功：" + outcome.courseCount + " 门课程"))
                }
                is ImportOutcome.Failure -> sendEvent(UiEvent.ShowMessage(outcome.message))
            }
        }
    }

    private companion object {
        val WEEKDAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    }
}
