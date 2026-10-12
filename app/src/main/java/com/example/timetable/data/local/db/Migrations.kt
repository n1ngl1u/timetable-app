package com.example.timetable.data.local.db

import androidx.room.migration.Migration

/**
 * 迁移清单。禁止使用 destructive fallback。
 */
object Migrations {

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
