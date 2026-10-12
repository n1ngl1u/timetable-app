package com.example.timetable.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2：reminders 增加 weeks 列（生效周次）。
 * 存储格式与 courses.weeks 统一为逗号分隔字符串，默认空串表示「跟随课程周次」。
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN weeks TEXT NOT NULL DEFAULT ''")
    }
}
