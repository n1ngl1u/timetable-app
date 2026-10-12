package com.example.timetable.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.example.timetable.domain.calendar.CalendarEventSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * CalendarContract 的唯一接触面。所有查询都带精确 where（_ID 或 CALENDAR_ID），
 * 不做全表扫描；调用方负责在 IO 线程执行（本类不做线程切换，便于组合）。
 */
@Singleton
class CalendarContractClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: CalendarPermissionChecker,
) {

    private val resolver: ContentResolver get() = context.contentResolver

    private val calendarMutex = Mutex()

    @Volatile
    private var cachedCalendarId: Long? = null

    /**
     * 查找或创建「课程表」专用日历。Mutex 保证并发下只创建一次；
     * 权限不足或创建失败返回 null，不抛异常。
     */
    suspend fun ensureCourseCalendar(): Long? = calendarMutex.withLock {
        cachedCalendarId?.let { return@withLock it }
        if (!permissionChecker.hasAllPermissions()) return@withLock null
        val id = findCourseCalendar() ?: createCourseCalendar()
        if (id != null) cachedCalendarId = id
        id
    }

    /** 本应用专用日历里所有事件的 id。作用域限定在 CALENDAR_ID，不是全表扫描。 */
    fun eventIdsInCalendar(calendarId: Long): Set<Long> {
        val ids = mutableSetOf<Long>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            CalendarContract.Events.CALENDAR_ID + " = ?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            while (cursor.moveToNext()) {
                ids += cursor.getLong(idIndex)
            }
        }
        return ids
    }

    fun insertEvent(calendarId: Long, spec: CalendarEventSpec): Long? {
        val values = spec.toContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        return ContentUris.parseId(uri)
    }

    fun updateEvent(eventId: Long, spec: CalendarEventSpec): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val affected = resolver.update(uri, spec.toContentValues(), null, null)
        return affected >= 0
    }

    /** 事件已不存在也视为成功，这样 link 记录可以被清理掉。 */
    fun deleteEvent(eventId: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.delete(uri, null, null)
        return true
    }

    private fun CalendarEventSpec.toContentValues(): ContentValues = ContentValues().apply {
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.EVENT_LOCATION, location)
        put(CalendarContract.Events.DESCRIPTION, description)
        put(CalendarContract.Events.DTSTART, startMillis)
        put(CalendarContract.Events.DTEND, endMillis)
        put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
        put(CalendarContract.Events.ALL_DAY, 0)
        put(CalendarContract.Events.RRULE, rrule)
    }

    private fun findCourseCalendar(): Long? = queryCalendarId(
        selection = CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " = ?",
        selectionArgs = arrayOf(CALENDAR_DISPLAY_NAME),
    )

    private fun createCourseCalendar(): Long? {
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

    /** 设备默认账号 = 日历列表里第一条带 ACCOUNT_NAME 的记录，不询问用户。 */
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

    companion object {
        const val CALENDAR_DISPLAY_NAME = "课程表"
        private const val CALENDAR_COLOR = -0x1a237e
        private const val TAG = "TimetableCalendar"
        val LOCAL_ACCOUNT = "Timetable" to CalendarContract.ACCOUNT_TYPE_LOCAL

        fun eventsUriFor(eventId: Long): Uri =
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
    }
}
