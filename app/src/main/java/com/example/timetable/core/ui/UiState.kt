package com.example.timetable.core.ui

import com.example.timetable.core.common.AppError

/**
 * 页面状态唯一出口，禁止 UI 层吞异常。
 */
sealed interface UiState<out T> {

    data object Idle : UiState<Nothing>

    data object Loading : UiState<Nothing>

    data class Success<T>(val data: T) : UiState<T>

    data object Empty : UiState<Nothing>

    data class Error(val error: AppError) : UiState<Nothing>
}
