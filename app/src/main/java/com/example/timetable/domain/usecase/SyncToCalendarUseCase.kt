package com.example.timetable.domain.usecase

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.domain.calendar.CalendarSkipReason
import com.example.timetable.domain.calendar.CalendarSyncPlanner
import com.example.timetable.domain.calendar.CalendarSyncResult
import com.example.timetable.domain.calendar.CourseEventMapper
import com.example.timetable.domain.repository.CalendarRepository
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * 把当前活跃课表增量同步到系统日历。
 *
 * 顺序：权限检查 -> 活跃课表 -> 先清孤儿 -> 逐课映射 -> diff -> 应用。
 * 先清孤儿是必要的：模块 1 的 upsertTimetable 是整表替换，重新导入会换掉课程 id，
 * 外键 CASCADE 会带走 link 记录，系统日历里的事件就成了永久孤儿。
 */
class SyncToCalendarUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val timetableRepository: TimetableRepository,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {

    suspend operator fun invoke(
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): CalendarSyncResult = withContext(dispatchers.io) {
        if (!calendarRepository.hasCalendarPermissions()) {
            return@withContext CalendarSyncResult.Skipped(
                CalendarSkipReason.PERMISSION_DENIED,
                "未授予日历权限，已降级为仅本地展示",
            )
        }
        val timetableId = settingsRepository.current().activeTimetableId
            ?: return@withContext CalendarSyncResult.Skipped(CalendarSkipReason.NO_ACTIVE_TIMETABLE)
        val timetable = timetableRepository.timetable(timetableId)
            ?: return@withContext CalendarSyncResult.Skipped(CalendarSkipReason.NO_ACTIVE_TIMETABLE)
        if (timetable.courses.isEmpty()) {
            return@withContext CalendarSyncResult.Skipped(CalendarSkipReason.NO_COURSES)
        }

        val pruned = calendarRepository.pruneOrphanEvents()
        val links = calendarRepository.linksForCourses(timetable.courses.map { it.id })
        val mappings = timetable.courses.map { course ->
            CourseEventMapper.map(timetable, course, zoneId)
        }
        val plan = CalendarSyncPlanner.plan(mappings, links)

        when (val result = calendarRepository.applyChanges(plan)) {
            is CalendarSyncResult.Skipped -> result
            is CalendarSyncResult.Failed -> result
            is CalendarSyncResult.Synced -> CalendarSyncResult.Synced(
                result.outcome.copy(prunedOrphans = pruned),
            )
        }
    }
}
