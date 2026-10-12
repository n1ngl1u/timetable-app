package com.example.timetable.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.domain.model.Course
import com.example.timetable.domain.model.TimeSlot
import com.example.timetable.domain.model.Timetable
import com.example.timetable.domain.repository.CalendarRepository
import com.example.timetable.domain.repository.TimetableRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * 单向同步（A7）：App -> 系统日历，写入设备默认账号下的「课程表」专用日历，不询问账号。
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timetableRepository: TimetableRepository,
    private val dispatchers: DispatcherProvider,
) : CalendarRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    private val zoneId: ZoneId get() = ZoneId.systemDefault()

    override suspend fun syncTimetable(timetableId: Long): Int = withContext(dispatchers.io) {
        val timetable = timetableRepository.timetable(timetableId) ?: return@withContext 0
        val calendarId = ensureCourseCalendar() ?: return@withContext 0
        clearSyncedEvents()
        var inserted = 0
        for (course in timetable.courses) {
            val startTime = startTimeOf(timetable, course) ?: continue
            val endTime = endTimeOf(timetable, course) ?: continue
            for (week in course.weeks) {
                val date = timetable.startDate
                    .plusWeeks((week - 1).toLong())
                    .plusDays((course.weekday - 1).toLong())
                val startMillis = LocalDateTime.of(date, startTime)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
                val endMillis = LocalDateTime.of(date, endTime)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
                if (insertEvent(calendarId, course, startMillis, endMillis) != null) {
                    inserted++
                }
            }
        }
        inserted
    }

    override suspend fun clearSyncedEvents(): Int = withContext(dispatchers.io) {
        val calendarId = findCourseCalendar() ?: return@withContext 0
        resolver.delete(
            CalendarContract.Events.CONTENT_URI,
            CalendarContract.Events.CALENDAR_ID + " = ?",
            arrayOf(calendarId.toString()),
        )
    }

    private fun insertEvent(
        calendarId: Long,
        course: Course,
        startMillis: Long,
        endMillis: Long,
    ): Uri? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, course.name)
            put(CalendarContract.Events.EVENT_LOCATION, course.location)
            put(CalendarContract.Events.DESCRIPTION, descriptionOf(course))
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, zoneId.id)
        }
        return resolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }

    private fun descriptionOf(course: Course): String =
        if (course.teacher.isBlank()) course.location else course.teacher + " / " + course.location

    private fun startTimeOf(timetable: Timetable, course: Course): LocalTime? =
        timeOf(timetable, course.startSection) { slot -> slot.startTime }

    private fun endTimeOf(timetable: Timetable, course: Course): LocalTime? =
        timeOf(timetable, course.endSection) { slot -> slot.endTime }

    private fun timeOf(
        timetable: Timetable,
        section: Int,
        selector: (TimeSlot) -> String,
    ): LocalTime? {
        val slot = timetable.timeSlots.firstOrNull { it.number == section } ?: return null
        return try {
            LocalTime.parse(selector(slot))
        } catch (error: DateTimeParseException) {
            null
        }
    }

    private fun ensureCourseCalendar(): Long? {
        findCourseCalendar()?.let { return it }
        val account = findDefaultAccount() ?: LOCAL_ACCOUNT
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, account.first)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, account.second)
            put(CalendarContract.Calendars.NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, account.first)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.first)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.second)
            .build()
        val inserted = resolver.insert(uri, values) ?: return null
        return ContentUris.parseId(inserted)
    }

    private fun findCourseCalendar(): Long? = queryCalendarId(
        selection = CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " = ?",
        selectionArgs = arrayOf(CALENDAR_DISPLAY_NAME),
    )

    private fun findDefaultAccount(): Pair<String, String>? {
        val cursor = resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
            ),
            CalendarContract.Calendars.ACCOUNT_NAME + " IS NOT NULL",
            null,
            CalendarContract.Calendars._ID + " ASC",
        ) ?: return null
        cursor.use { activeCursor ->
            if (!activeCursor.moveToFirst()) return null
            val accountName = activeCursor.getString(0) ?: return null
            val accountType = activeCursor.getString(1) ?: return null
            return accountName to accountType
        }
    }

    private fun queryCalendarId(selection: String, selectionArgs: Array<String>): Long? {
        val cursor = resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            selection,
            selectionArgs,
            null,
        ) ?: return null
        cursor.use { activeCursor ->
            if (!activeCursor.moveToFirst()) return null
            return activeCursor.getLong(0)
        }
    }

    private companion object {
        const val CALENDAR_DISPLAY_NAME = "课程表"
        const val CALENDAR_COLOR = -0x1a237e
        val LOCAL_ACCOUNT = "Timetable" to CalendarContract.ACCOUNT_TYPE_LOCAL
    }
}
