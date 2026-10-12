package com.example.timetable.domain.usecase

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.domain.calendar.CalendarSkipReason
import com.example.timetable.domain.calendar.CalendarSyncResult
import com.example.timetable.domain.repository.CalendarRepository
import com.example.timetable.domain.repository.TimetableRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext

/** 删除课程的结果，供 UI 决定提示文案。 */
sealed interface DeleteCourseResult {

    data object Deleted : DeleteCourseResult

    /** 系统日历侧写失败：课程不删，link 保留以便重试。 */
    data class AbortedByCalendar(val message: String) : DeleteCourseResult

    /** 日历权限被撤销：仍然删课程，但系统日历里可能残留旧事件。 */
    data object DeletedWithResidualCalendarEvents : DeleteCourseResult
}

/**
 * 课程删除的唯一编排点。顺序不可颠倒：
 * 先删系统日历事件，再删课程 —— link 表外键是 ON DELETE CASCADE，
 * 先删课程会让 link 先消失，系统日历里的事件就再也找不到归属。
 */
class DeleteCourseUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val timetableRepository: TimetableRepository,
    private val dispatchers: DispatcherProvider,
) {

    suspend operator fun invoke(courseId: Long): DeleteCourseResult = withContext(dispatchers.io) {
        when (val calendarResult = calendarRepository.deleteEventsForCourse(courseId)) {
            is CalendarSyncResult.Failed -> {
                return@withContext DeleteCourseResult.AbortedByCalendar(calendarResult.message)
            }
            is CalendarSyncResult.Skipped -> {
                // 没权限就不动了系统日历；课程照删，残留事件由下次同步的 pruneOrphanEvents 兜底
                timetableRepository.deleteCourse(courseId)
                if (calendarResult.reason == CalendarSkipReason.PERMISSION_DENIED) {
                    DeleteCourseResult.DeletedWithResidualCalendarEvents
                } else {
                    DeleteCourseResult.Deleted
                }
            }
            is CalendarSyncResult.Synced -> {
                timetableRepository.deleteCourse(courseId)
                DeleteCourseResult.Deleted
            }
        }
    }
}
