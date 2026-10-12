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

/** 精确闹钟触发点：投递通知并排下一次。 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getLongExtra(ReminderScheduler.EXTRA_RULE_ID, NO_RULE)
        if (ruleId < 0L) return
        val pendingResult = goAsync()
        val applicationContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                EntryPointAccessors
                    .fromApplication(applicationContext, ReminderEntryPoint::class.java)
                    .reminderScheduler()
                    .onTriggered(ruleId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val NO_RULE = -1L
    }
}
