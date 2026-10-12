package com.example.timetable.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.timetable.di.ReminderEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * 降级路径的执行体。走 Hilt EntryPoint 而非 @HiltWorker：
 * 本工程已有 WidgetEntryPoint 的同款做法，无需再引 hilt-work 依赖、
 * 也无需改 Application 的 Configuration.Provider 与清单里的 WorkManager 初始化。
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ruleId = inputData.getLong(KEY_RULE_ID, NO_RULE)
        if (ruleId < 0L) return Result.failure()
        val scheduler = EntryPointAccessors
            .fromApplication(applicationContext, ReminderEntryPoint::class.java)
            .reminderScheduler()
        scheduler.onTriggered(ruleId)
        return Result.success()
    }

    companion object {
        const val KEY_RULE_ID = "rule_id"
        private const val NO_RULE = -1L
    }
}
