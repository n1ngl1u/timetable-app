package com.example.timetable.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.timetable.di.ReminderEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 开机 / 时区变化 / 系统时间变更后重排。时间基准变了，之前算出的触发时刻全部作废。
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        runReschedule(context.applicationContext, goAsync())
    }
}

/**
 * 用户在系统设置里授权或撤销精确闹钟后重排：撤销时自动切到 WorkManager 降级路径。
 */
class ExactAlarmPermissionChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        runReschedule(context.applicationContext, goAsync())
    }
}

private fun runReschedule(
    applicationContext: Context,
    pendingResult: BroadcastReceiver.PendingResult?,
) {
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            EntryPointAccessors
                .fromApplication(applicationContext, ReminderEntryPoint::class.java)
                .reminderScheduler()
                .rescheduleAll()
        } finally {
            pendingResult?.finish()
        }
    }
}
