package com.example.timetable.ui.puremode

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.R
import com.example.timetable.core.time.TimeProvider
import com.example.timetable.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 文案以资源 ID 传递：MainActivity 不是 Composable，拿不到 stringResource。 */
sealed interface PureModeMessage {

    @get:StringRes
    val textRes: Int

    /** 首次进入纯净模式时的一次性提示。 */
    data object HintFirstEntry : PureModeMessage {
        override val textRes: Int = R.string.pure_mode_hint_first_entry
    }

    /** 第一次返回键：提示再按一次。 */
    data object ConfirmExit : PureModeMessage {
        override val textRes: Int = R.string.pure_mode_confirm_exit
    }
}

/**
 * 纯净模式的唯一真源。Activity 作用域：开关状态、双击计时、首次提示都只有这一份，
 * 系统栏与导航栏的渲染都派生自这里的 enabled。
 *
 * 不做「临时退出」语义：双击返回就是关闭开关并落 DataStore，
 * 下次启动不再进纯净模式。两套状态源会带来「界面已退出但开关还开着」这类不可解释的状态。
 */
@HiltViewModel
class PureModeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val mutableMessages = MutableSharedFlow<PureModeMessage>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val messages: SharedFlow<PureModeMessage> = mutableMessages.asSharedFlow()

    val enabled: StateFlow<Boolean> = settingsRepository.settings
        .map { settings -> settings.pureModeEnabled }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * 双击窗口的上次返回时间。只在主线程读写（BackHandler 在主线程回调），
     * 也不需要驱动重组，因此用普通字段而不是 StateFlow。
     */
    private var lastBackMillis: Long? = null

    init {
        // 提示的唯一发出点：观察 false -> true 的跳变。
        // 具体是否真的发，由持久化的 hintShown 位决定，所以跨进程重建也只会发一次。
        viewModelScope.launch {
            var previous: Boolean? = null
            enabled.collect { current ->
                val wasEnabled = previous
                previous = current
                if (wasEnabled == false && current) {
                    announceFirstEntryIfNeeded()
                }
            }
        }
    }

    /**
     * 同步返回「是否应当退出纯净模式」，持久化异步进行。
     * 同步是必须的：BackHandler 要在同一帧里决定是否消费这次返回。
     */
    fun onBackPressed(): Boolean {
        if (!enabled.value) return false
        val evaluation = DoubleBackDetector.evaluate(
            nowMillis = timeProvider.elapsedRealtimeMillis(),
            lastBackMillis = lastBackMillis,
        )
        lastBackMillis = evaluation.nextLastBackMillis
        if (!evaluation.shouldExit) {
            mutableMessages.tryEmit(PureModeMessage.ConfirmExit)
            return false
        }
        viewModelScope.launch {
            settingsRepository.update { it.copy(pureModeEnabled = false) }
        }
        return true
    }

    /** 发出即置位，不依赖调用方回调，避免提示重复弹出。 */
    private suspend fun announceFirstEntryIfNeeded() {
        if (settingsRepository.current().pureModeHintShown) return
        settingsRepository.update { it.copy(pureModeHintShown = true) }
        mutableMessages.tryEmit(PureModeMessage.HintFirstEntry)
    }
}
