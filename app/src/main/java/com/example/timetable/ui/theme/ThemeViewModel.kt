package com.example.timetable.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.core.time.TimeProvider
import com.example.timetable.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 主题偏好的唯一来源。Activity 作用域：主题必须在 TimetableTheme 之上求值，
 * 而 TimetableTheme 包住整个 setContent。
 *
 * 只暴露偏好与时段，不产出 ThemeTokens —— 动态取色需要 Context，
 * 而 isSystemInDarkTheme 只能在组合里读，两者都在 TimetableThemeHost 里合并。
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    data class ThemeRequest(
        val themeId: ThemeId,
        val darkModePreference: DarkModePreference,
        val period: DayPeriod,
    )

    private val tickerActive = MutableStateFlow(false)

    private val ticks = MutableStateFlow(0L)

    /**
     * 初值同步算出：设置项还没读回来时至少时段是对的，
     * 避免首帧落到 NIGHT 造成可见的闪一下。
     */
    private val initial = ThemeRequest(
        themeId = ThemeId.FALLBACK,
        darkModePreference = DarkModePreference.FALLBACK,
        period = currentPeriod(),
    )

    val request: StateFlow<ThemeRequest> = combine(
        settingsRepository.settings,
        tickerActive,
        ticks,
    ) { settings, _, _ ->
        ThemeRequest(
            themeId = ThemeId.fromStorage(settings.themeId),
            darkModePreference = DarkModePreference.fromStorage(settings.darkModePreference),
            period = currentPeriod(),
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    private var tickerJob: Job? = null

    /** 由 LifecycleStartEffect 驱动：STARTED 起跑，STOP 即停，不在后台空转。 */
    fun setTickerActive(active: Boolean) {
        if (tickerActive.value == active) return
        tickerActive.value = active
        tickerJob?.cancel()
        tickerJob = if (active) {
            viewModelScope.launch {
                while (currentCoroutineContext().isActive) {
                    ticks.value = ticks.value + 1
                    delay(TICK_INTERVAL_MILLIS)
                }
            }
        } else {
            null
        }
    }

    /** 周期 1 分钟：11:00 / 17:00 / 20:00 三个边界最迟 60 秒内被观察到。 */
    private fun currentPeriod(): DayPeriod {
        val zone = ZoneId.systemDefault()
        val localTime = LocalTime.from(Instant.ofEpochMilli(timeProvider.epochMillis()).atZone(zone))
        return DayPeriodResolver.resolve(localTime)
    }

    companion object {
        const val TICK_INTERVAL_MILLIS = 60_000L
    }
}
