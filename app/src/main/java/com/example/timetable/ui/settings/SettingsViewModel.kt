package com.example.timetable.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.core.ui.UiEvent
import com.example.timetable.core.ui.UiState
import com.example.timetable.domain.calendar.CalendarSkipReason
import com.example.timetable.domain.calendar.CalendarSyncResult
import com.example.timetable.domain.repository.CalendarRepository
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.usecase.SyncToCalendarUseCase
import com.example.timetable.ui.theme.DarkModePreference
import com.example.timetable.ui.theme.ThemeId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SettingsUiModel(
    val pureModeEnabled: Boolean,
    val themeId: ThemeId,
    val darkModePreference: DarkModePreference,
    val calendarSyncEnabled: Boolean,
    val calendarPermissionGranted: Boolean,
    val lastSyncSummary: String?,
) {
    val showCalendarPermissionHint: Boolean
        get() = calendarSyncEnabled && !calendarPermissionGranted
}

/**
 * 设置页。日历同步开关是本批唯一有实质行为的设置项；
 * 主题 / 纯净模式 / 加密设置留给模块 7/8/9，本类不为它们预埋空状态。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val calendarRepository: CalendarRepository,
    private val syncToCalendar: SyncToCalendarUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow<UiState<SettingsUiModel>>(UiState.Loading)

    val state: StateFlow<UiState<SettingsUiModel>> = mutableState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<UiEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<UiEvent> = mutableEvents.asSharedFlow()

    private val calendarPermissionGranted = MutableStateFlow(false)

    private val lastSyncSummary = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                calendarPermissionGranted,
                lastSyncSummary,
            ) { settings, permissionGranted, summary ->
                SettingsUiModel(
                    pureModeEnabled = settings.pureModeEnabled,
                    themeId = ThemeId.fromStorage(settings.themeId),
                    darkModePreference = DarkModePreference.fromStorage(settings.darkModePreference),
                    calendarSyncEnabled = settings.calendarSyncEnabled,
                    calendarPermissionGranted = permissionGranted,
                    lastSyncSummary = summary,
                )
            }.collect { model -> mutableState.value = UiState.Success(model) }
        }
        viewModelScope.launch { refreshPermissionState() }
    }

    /**
     * 只写开关，不发任何消息 —— 首次进入提示由 PureModeViewModel 观察
     * pureModeEnabled 的 false -> true 跳变后发出，保证提示只有一个发出点。
     */
    fun onPureModeChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setPureModeEnabled(enabled) }
        }
    }

    /**
     * 乐观更新：DataStore 的 edit 会先把新值推给 data flow、再落盘，
     * 所以 UI 立刻切换，磁盘写入在后台进行；写失败静默吞掉，
     * 表现是下次启动回到旧主题，而不是弹一个用户无法处理的错误。
     */
    fun onThemeSelected(themeId: ThemeId) {
        viewModelScope.launch {
            runCatching { settingsRepository.update { it.copy(themeId = themeId.storageKey) } }
        }
    }

    fun onDarkModePreferenceChange(preference: DarkModePreference) {
        viewModelScope.launch {
            runCatching {
                settingsRepository.update { it.copy(darkModePreference = preference.storageKey) }
            }
        }
    }

    /**
     * 打开：先要权限；没权限就发请求事件并保持开关关闭。
     * 关闭：清掉已写入系统日历的事件，让开关语义对称（不留半同步状态）。
     */
    fun onCalendarSyncToggle(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !calendarRepository.hasCalendarPermissions()) {
                mutableEvents.tryEmit(UiEvent.RequestCalendarPermission)
                lastSyncSummary.value = null
                refreshPermissionState()
                return@launch
            }
            settingsRepository.update { it.copy(calendarSyncEnabled = enabled) }
            if (enabled) {
                runSync()
            } else {
                runClear()
            }
            refreshPermissionState()
        }
    }

    /** 权限弹窗或系统设置返回后刷新状态；此时若开关已打开则补一次同步。 */
    fun onPermissionStateMaybeChanged() {
        viewModelScope.launch {
            refreshPermissionState()
            if (calendarPermissionGranted.value && isSyncEnabled()) {
                runSync()
            }
        }
    }

    private suspend fun isSyncEnabled(): Boolean = settingsRepository.current().calendarSyncEnabled

    private suspend fun runSync() {
        when (val result = syncToCalendar()) {
            is CalendarSyncResult.Synced -> {
                val outcome = result.outcome
                lastSyncSummary.value = if (outcome.inserted + outcome.updated + outcome.deleted == 0) {
                    "已是最新，无需改动"
                } else {
                    "已同步：新增 " + outcome.inserted + "，更新 " + outcome.updated +
                        "，删除 " + outcome.deleted
                }
                if (outcome.skippedCount > 0) {
                    mutableEvents.tryEmit(
                        UiEvent.ShowMessage(outcome.skippedCount.toString() + " 门课缺少节次时间映射，已跳过"),
                    )
                }
            }
            is CalendarSyncResult.Skipped -> {
                lastSyncSummary.value = null
                if (result.reason == CalendarSkipReason.PERMISSION_DENIED) {
                    refreshPermissionState()
                } else {
                    mutableEvents.tryEmit(UiEvent.ShowMessage("暂无可同步的课程"))
                }
            }
            is CalendarSyncResult.Failed -> {
                lastSyncSummary.value = null
                mutableEvents.tryEmit(UiEvent.ShowMessage(result.message))
            }
        }
    }

    private suspend fun runClear() {
        when (val result = calendarRepository.clearSyncedEvents()) {
            is CalendarSyncResult.Synced -> {
                lastSyncSummary.value = "已移除系统日历中的课程事件"
            }
            is CalendarSyncResult.Skipped -> {
                lastSyncSummary.value = null
                if (result.reason != CalendarSkipReason.PERMISSION_DENIED) {
                    mutableEvents.tryEmit(UiEvent.ShowMessage("清除系统日历事件失败"))
                }
            }
            is CalendarSyncResult.Failed -> {
                lastSyncSummary.value = null
                mutableEvents.tryEmit(UiEvent.ShowMessage(result.message))
            }
        }
    }

    private suspend fun refreshPermissionState() {
        calendarPermissionGranted.value = calendarRepository.hasCalendarPermissions()
    }
}
