package com.example.timetable.di

import com.example.timetable.domain.usecase.BuildWidgetSnapshotUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * GlanceAppWidget 由系统实例化，无法构造注入，只能从 Application 组件取依赖。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun buildWidgetSnapshotUseCase(): BuildWidgetSnapshotUseCase
}
