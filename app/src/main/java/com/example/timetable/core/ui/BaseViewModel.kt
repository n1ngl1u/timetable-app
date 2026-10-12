package com.example.timetable.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.core.common.AppError
import com.example.timetable.core.common.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ViewModel 基类：统一持有一次性事件通道，并把异常收敛为 [AppResult.Failure]。
 */
abstract class BaseViewModel : ViewModel() {

    private val mutableEvents = MutableSharedFlow<UiEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<UiEvent> = mutableEvents.asSharedFlow()

    protected fun sendEvent(event: UiEvent) {
        mutableEvents.tryEmit(event)
    }

    protected fun launchSafe(block: suspend () -> Unit): Job = viewModelScope.launch {
        block()
    }

    protected suspend fun <T> runCatchingUi(
        errorMapper: (Throwable) -> AppError,
        block: suspend () -> T,
    ): AppResult<T> = try {
        AppResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        AppResult.Failure(errorMapper(error))
    }
}
