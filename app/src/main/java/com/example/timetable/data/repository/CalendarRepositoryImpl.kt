package com.example.timetable.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.core.time.TimeProvider
import com.example.timetable.data.calendar.CalendarContractClient
import com.example.timetable.data.calendar.CalendarPermissionChecker
import com.example.timetable.data.local.dao.CalendarEventLinkDao
import com.example.timetable.data.local.db.AppDatabase
import com.example.timetable.data.local.entity.CalendarEventLinkEntity
import com.example.timetable.domain.calendar.CalendarEventLink
import com.example.timetable.domain.calendar.CalendarEventSpec
import com.example.timetable.domain.calendar.CalendarSkipReason
import com.example.timetable.domain.calendar.CalendarSyncOutcome
import com.example.timetable.domain.calendar.CalendarSyncPlan
import com.example.timetable.domain.calendar.CalendarSyncResult
import com.example.timetable.domain.repository.CalendarRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val linkDao: CalendarEventLinkDao,
    private val client: CalendarContractClient,
    private val permissionChecker: CalendarPermissionChecker,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
) : CalendarRepository {

    override suspend fun hasCalendarPermissions(): Boolean =
        withContext(dispatchers.io) { permissionChecker.hasAllPermissions() }

    override suspend fun ensureCourseCalendar(): Long? =
        withContext(dispatchers.io) { client.ensureCourseCalendar() }

    override suspend fun linksForCourses(courseIds: List<Long>): List<CalendarEventLink> =
        withContext(dispatchers.io) {
            if (courseIds.isEmpty()) {
                emptyList()
            } else {
                linkDao.findByCourseIds(courseIds).map { it.toDomain() }
            }
        }

    override suspend fun applyChanges(plan: CalendarSyncPlan): CalendarSyncResult =
        withContext(dispatchers.io) {
            if (!permissionChecker.hasAllPermissions()) {
                return@withContext CalendarSyncResult.Skipped(
                    CalendarSkipReason.PERMISSION_DENIED,
                    "未授予读取/写入日历权限",
                )
            }
            val calendarId = client.ensureCourseCalendar()
                ?: return@withContext CalendarSyncResult.Skipped(
                    CalendarSkipReason.NO_WRITABLE_CALENDAR,
                    "无法创建或找到可写的「课程表」日历",
                )

            val createdEventIds = mutableListOf<Long>()
            val staleEventIds = mutableListOf<Long>()
            val newLinks = mutableListOf<CalendarEventLinkEntity>()
            val now = timeProvider.epochMillis()

            try {
                for (insert in plan.inserts) {
                    val eventId = client.insertEvent(calendarId, insert.spec)
                        ?: throw IllegalStateException(
                            "插入日历事件失败 courseId=" + insert.courseId,
                        )
                    createdEventIds += eventId
                    newLinks += insert.spec.toEntity(insert.courseId, eventId, now)
                }
                for (update in plan.updates) {
                    client.updateEvent(update.calendarEventId, update.spec)
                    staleEventIds += update.calendarEventId
                    newLinks += update.spec.toEntity(update.courseId, update.calendarEventId, now)
                }
                for (delete in plan.deletes) {
                    client.deleteEvent(delete.calendarEventId)
                    staleEventIds += delete.calendarEventId
                }
            } catch (error: Exception) {
                // CalendarProvider 没有跨条事务：只能补偿删除本次新建的事件。
                // 单条补偿失败不中断其余补偿，失败的 event id 打进日志供人工清理。
                val failedCompensations = mutableListOf<Long>()
                for (eventId in createdEventIds) {
                    runCatching { client.deleteEvent(eventId) }
                        .onFailure { failedCompensations += eventId }
                }
                if (failedCompensations.isNotEmpty()) {
                    Log.e(
                        TAG,
                        "补偿回滚失败，残留孤儿事件 eventIds=" + failedCompensations +
                            "（可在系统日历「课程表」中手动删除，或调 clearSyncedEvents 全量重建）",
                    )
                }
                return@withContext CalendarSyncResult.Failed(
                    error.message ?: "写入系统日历失败，已回滚本次新建事件",
                )
            }

            database.withTransaction {
                if (staleEventIds.isNotEmpty()) linkDao.deleteByEventIds(staleEventIds)
                if (newLinks.isNotEmpty()) linkDao.insertAll(newLinks)
            }

            CalendarSyncResult.Synced(
                CalendarSyncOutcome(
                    inserted = plan.inserts.size,
                    updated = plan.updates.size,
                    deleted = plan.deletes.size,
                    unchanged = plan.unchanged.size,
                    failed = 0,
                    skippedCourses = plan.skippedCourses,
                ),
            )
        }

    override suspend fun deleteEventsForCourse(courseId: Long): CalendarSyncResult =
        withContext(dispatchers.io) {
            if (!permissionChecker.hasAllPermissions()) {
                return@withContext CalendarSyncResult.Skipped(
                    CalendarSkipReason.PERMISSION_DENIED,
                    "未授予读取/写入日历权限",
                )
            }
            val links = linkDao.findByCourseIds(listOf(courseId))
            if (links.isEmpty()) {
                return@withContext CalendarSyncResult.Synced(CalendarSyncOutcome())
            }
            val removed = mutableListOf<Long>()
            val failed = mutableListOf<Long>()
            for (link in links) {
                runCatching { client.deleteEvent(link.calendarEventId) }
                    .onSuccess { removed += link.calendarEventId }
                    .onFailure {
                        failed += link.calendarEventId
                        Log.w(TAG, "删除日历事件失败 eventId=" + link.calendarEventId)
                    }
            }
            // 只清掉确认删除成功的 link，失败的保留以便下次重试
            if (removed.isNotEmpty()) linkDao.deleteByEventIds(removed)
            CalendarSyncResult.Synced(
                CalendarSyncOutcome(deleted = removed.size, failed = failed.size),
            )
        }

    override suspend fun pruneOrphanEvents(): Int = withContext(dispatchers.io) {
        if (!permissionChecker.hasAllPermissions()) return@withContext 0
        val calendarId = client.ensureCourseCalendar() ?: return@withContext 0
        val inCalendar = client.eventIdsInCalendar(calendarId)
        val linked = linkDao.findAll().map { it.calendarEventId }.toSet()

        // 指向已消失事件的 link：用户可能手工删过事件，留着只会让 diff 误判为「已存在」
        val danglingLinks = linked - inCalendar
        if (danglingLinks.isNotEmpty()) linkDao.deleteByEventIds(danglingLinks.toList())

        val orphans = inCalendar - linked
        if (orphans.isEmpty()) return@withContext 0
        var pruned = 0
        for (eventId in orphans) {
            runCatching { client.deleteEvent(eventId) }
                .onSuccess { pruned++ }
                .onFailure { Log.w(TAG, "孤儿事件清理失败 eventId=" + eventId) }
        }
        pruned
    }

    override suspend fun clearSyncedEvents(): CalendarSyncResult = withContext(dispatchers.io) {
        if (!permissionChecker.hasAllPermissions()) {
            return@withContext CalendarSyncResult.Skipped(
                CalendarSkipReason.PERMISSION_DENIED,
                "未授予读取/写入日历权限",
            )
        }
        val calendarId = client.ensureCourseCalendar()
            ?: return@withContext CalendarSyncResult.Skipped(
                CalendarSkipReason.NO_WRITABLE_CALENDAR,
                "无法创建或找到可写的「课程表」日历",
            )
        val eventIds = client.eventIdsInCalendar(calendarId)
        var deleted = 0
        val failed = mutableListOf<Long>()
        for (eventId in eventIds) {
            runCatching { client.deleteEvent(eventId) }
                .onSuccess { deleted++ }
                .onFailure {
                    failed += eventId
                    Log.w(TAG, "清空日历事件失败 eventId=" + eventId)
                }
        }
        if (failed.isEmpty()) {
            linkDao.deleteAll()
        } else {
            Log.e(TAG, "清空未完成，保留 link 记录以便重试 failed=" + failed)
        }
        CalendarSyncResult.Synced(
            CalendarSyncOutcome(deleted = deleted, failed = failed.size),
        )
    }

    private fun CalendarEventSpec.toEntity(
        courseId: Long,
        eventId: Long,
        syncedAt: Long,
    ): CalendarEventLinkEntity = CalendarEventLinkEntity(
        courseId = courseId,
        calendarEventId = eventId,
        segmentStartWeek = segmentStartWeek,
        segmentEndWeek = segmentEndWeek,
        contentHash = contentHash,
        syncedAt = syncedAt,
    )

    private fun CalendarEventLinkEntity.toDomain(): CalendarEventLink = CalendarEventLink(
        id = id,
        courseId = courseId,
        calendarEventId = calendarEventId,
        segmentStartWeek = segmentStartWeek,
        segmentEndWeek = segmentEndWeek,
        contentHash = contentHash,
        syncedAt = syncedAt,
    )

    private companion object {
        const val TAG = "TimetableCalendar"
    }
}
