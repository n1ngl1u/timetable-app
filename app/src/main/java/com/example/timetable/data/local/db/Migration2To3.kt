package com.example.timetable.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 -> v3：新增 calendar_event_links 表，记录「课程 -> 系统日历事件」映射。
 * 业务唯一键是 (course_id, segment_start_week)：方案 A′ 下一门课按连续周次段对应多条事件。
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `calendar_event_links` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`course_id` INTEGER NOT NULL, " +
                "`calendar_event_id` INTEGER NOT NULL, " +
                "`segment_start_week` INTEGER NOT NULL, " +
                "`segment_end_week` INTEGER NOT NULL, " +
                "`content_hash` TEXT NOT NULL, " +
                "`synced_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`course_id`) REFERENCES `courses`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_calendar_event_links_course_id` " +
                "ON `calendar_event_links` (`course_id`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_calendar_event_links_course_id_segment_start_week` " +
                "ON `calendar_event_links` (`course_id`, `segment_start_week`)",
        )
    }
}
