package com.example.timetable.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timetable.data.local.entity.ReminderEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * 调度一次所需的全部字段投影：规则 + 课程 + 课表 + 该节次的开始时间。
 * weeks 列与 courses.weeks 同格式（逗号分隔），经 Converters 双向转换。
 */
data class ReminderScheduleRow(
    val ruleId: Long,
    val courseId: Long,
    val courseName: String,
    val location: String,
    val weekday: Int,
    val startSection: Int,
    val startTime: String?,
    val timetableId: Long,
    val timetableName: String,
    val timetableStartDate: LocalDate,
    val advanceMinutes: Int,
    val enabled: Boolean,
    val ruleWeeks: List<Int>,
    val courseWeeks: List<Int>,
)

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY id ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE course_id = :courseId ORDER BY id ASC")
    fun observeByCourse(courseId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE course_id = :courseId ORDER BY id ASC LIMIT 1")
    suspend fun findByCourse(courseId: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :ruleId")
    suspend fun findById(ruleId: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReminderEntity): Long

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :ruleId")
    suspend fun setEnabled(ruleId: Long, enabled: Boolean)

    @Query("DELETE FROM reminders WHERE id = :ruleId")
    suspend fun deleteById(ruleId: Long)

    @Query("DELETE FROM reminders WHERE course_id = :courseId")
    suspend fun deleteByCourse(courseId: Long)

    @Query(SCHEDULE_SELECT + " WHERE r.enabled = 1 ORDER BY r.id ASC")
    suspend fun findEnabledSchedules(): List<ReminderScheduleRow>

    @Query(SCHEDULE_SELECT + " WHERE r.id = :ruleId LIMIT 1")
    suspend fun findSchedule(ruleId: Long): ReminderScheduleRow?

    companion object {
        const val SCHEDULE_SELECT = "SELECT r.id AS ruleId, r.course_id AS courseId, " +
            "c.name AS courseName, c.location AS location, c.weekday AS weekday, " +
            "c.start_section AS startSection, s.start_time AS startTime, " +
            "t.id AS timetableId, t.table_name AS timetableName, t.start_date AS timetableStartDate, " +
            "r.advance_minutes AS advanceMinutes, r.enabled AS enabled, " +
            "r.weeks AS ruleWeeks, c.weeks AS courseWeeks " +
            "FROM reminders r " +
            "INNER JOIN courses c ON c.id = r.course_id " +
            "INNER JOIN timetables t ON t.id = c.timetable_id " +
            "LEFT JOIN time_slots s ON s.timetable_id = t.id AND s.number = c.start_section"
    }
}
