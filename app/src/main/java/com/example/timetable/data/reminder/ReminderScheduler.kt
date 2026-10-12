package com.example.timetable.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.core.time.TimeProvider
import com.example.timetable.domain.model.ReminderSchedule
import com.example.timetable.domain.reminder.ReminderDispatch
import com.example.timetable.domain.reminder.ReminderNotificationContentFactory
import com.example.timetable.domain.reminder.ReminderSchedulingPolicy
import com.example.timetable.domain.reminder.ReminderTriggerCalculator
import com.example.timetable.domain.reminder.SkipReason
import com.example.timetable.domain.repository.ReminderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * 统一调度入口。决策（走闹钟还是 WorkManager）由纯策略给出，这里只负责落地。
 *
 * 精确路径：AlarmManager.setExactAndAllowWhileIdle + FLAG_IMMUTABLE 的 PendingIntent。
 * 降级路径：OneTimeWorkRequest + initialDelay，唯一任务名按 ruleId 生成并 REPLACE，
 * 不重复入队；执行时机受 Doze/批处理影响，精度为 ±15min 级退化，不保证具体时刻。
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val permissionChecker: ReminderPermissionChecker,
    private val notifications: ReminderNotifications,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) {

    fun canScheduleExactAlarms(): Boolean = permissionChecker.canScheduleExactAlarms()

    fun notificationsAllowed(): Boolean = permissionChecker.notificationsAllowed()

    suspend fun rescheduleAll(): Int = withContext(dispatchers.io) {
        notifications.ensureChannel()
        var planned = 0
        for (schedule in reminderRepository.enabledSchedules()) {
            if (plan(schedule) !is ReminderDispatch.Skipped) planned++
        }
        planned
    }

    suspend fun reschedule(ruleId: Long): ReminderDispatch = withContext(dispatchers.io) {
        val schedule = reminderRepository.scheduleFor(ruleId)
        if (schedule == null) {
            ReminderDispatch.Skipped(ruleId, SkipReason.NO_TRIGGER_WINDOW)
        } else {
            plan(schedule)
        }
    }

    /** 触发后的统一动作：投递通知 + 排下一次。闹钟与 Worker 共用这一条路径。 */
    suspend fun onTriggered(ruleId: Long): ReminderDispatch = withContext(dispatchers.io) {
        val schedule = reminderRepository.scheduleFor(ruleId)
        if (schedule == null) {
            ReminderDispatch.Skipped(ruleId, SkipReason.NO_TRIGGER_WINDOW)
        } else {
            if (schedule.enabled) {
                notifications.notify(ReminderNotificationContentFactory.create(schedule))
            } else {
                notifications.cancel(ReminderNotificationContentFactory.notificationIdFor(ruleId))
            }
            plan(schedule)
        }
    }

    fun cancel(ruleId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager?.cancel(alarmPendingIntent(ruleId))
        WorkManager.getInstance(context).cancelUniqueWork(workNameFor(ruleId))
    }

    private suspend fun plan(schedule: ReminderSchedule): ReminderDispatch {
        val triggerAt = ReminderTriggerCalculator.nextTriggerAtMillis(
            schedule = schedule,
            nowMillis = timeProvider.epochMillis(),
            zone = ZoneId.systemDefault(),
        )
        val dispatch = ReminderSchedulingPolicy.decide(
            schedule = schedule,
            nextTriggerAtMillis = triggerAt,
            canScheduleExactAlarms = permissionChecker.canScheduleExactAlarms(),
        )
        when (dispatch) {
            is ReminderDispatch.Exact -> setExactAlarm(schedule.ruleId, dispatch.triggerAtMillis)
            is ReminderDispatch.Inexact -> enqueueFallbackWork(schedule.ruleId, dispatch.triggerAtMillis)
            is ReminderDispatch.Skipped -> cancel(schedule.ruleId)
        }
        return dispatch
    }

    private fun setExactAlarm(ruleId: Long, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = alarmPendingIntent(ruleId)
        alarmManager.cancel(pendingIntent)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    private fun enqueueFallbackWork(ruleId: Long, triggerAtMillis: Long) {
        val delayMillis = (triggerAtMillis - timeProvider.epochMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_RULE_ID to ruleId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workNameFor(ruleId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun alarmPendingIntent(ruleId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
            putExtra(EXTRA_RULE_ID, ruleId)
        }
        return PendingIntent.getBroadcast(
            context,
            ReminderNotificationContentFactory.notificationIdFor(ruleId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REMINDER_ALARM = "com.example.timetable.action.REMINDER_ALARM"
        const val EXTRA_RULE_ID = "extra_rule_id"

        const val REMINDER_WORK_PREFIX = "reminder_work_"

        fun workNameFor(ruleId: Long): String = REMINDER_WORK_PREFIX + ruleId
    }
}
