package com.example.timetable.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v2 -> v3 迁移：新建 calendar_event_links 表与索引，老数据保留。
 */
@RunWith(AndroidJUnit4::class)
class Migration2To3Test {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migrationCreatesLinkTableAndKeepsExistingRows() {
        context.deleteDatabase(DB_NAME)
        createVersion2Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase

            val columns = readColumns(sqlite, "calendar_event_links")
            assertEquals(
                setOf(
                    "id",
                    "course_id",
                    "calendar_event_id",
                    "segment_start_week",
                    "segment_end_week",
                    "content_hash",
                    "synced_at",
                ),
                columns.keys,
            )
            assertEquals(3, sqlite.version)

            sqlite.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'calendar_event_links'")
                .use { cursor ->
                    val names = mutableSetOf<String>()
                    while (cursor.moveToNext()) names += cursor.getString(0)
                    assertTrue(
                        "缺少 course_id 索引: " + names,
                        names.contains("index_calendar_event_links_course_id"),
                    )
                    assertTrue(
                        "缺少业务唯一索引: " + names,
                        names.contains("index_calendar_event_links_course_id_segment_start_week"),
                    )
                }

            sqlite.query("SELECT name, weeks FROM courses WHERE id = 1").use { cursor ->
                assertTrue("v2 的老数据必须保留", cursor.moveToFirst())
                assertEquals("数据结构", cursor.getString(0))
                assertEquals("1,2,3", cursor.getString(1))
            }
        } finally {
            database.close()
            context.deleteDatabase(DB_NAME)
        }
    }

    private fun createVersion2Database() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        V2_DDL.forEach { statement -> db.execSQL(statement) }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO semesters (id, name, start_date, major, is_active) " +
                    "VALUES (1, '2026 秋', 20000, '', 1)",
            )
            db.execSQL(
                "INSERT INTO timetables (id, table_name, start_date, semester_id) " +
                    "VALUES (1, '主课表', 20000, 1)",
            )
            db.execSQL(
                "INSERT INTO courses (id, timetable_id, name, teacher, location, weekday, " +
                    "start_section, end_section, weeks) " +
                    "VALUES (1, 1, '数据结构', '王强', '计算机楼 402', 2, 1, 2, '1,2,3')",
            )
            db.execSQL(
                "INSERT INTO reminders (id, course_id, advance_minutes, enabled, weeks) " +
                    "VALUES (1, 1, 15, 1, '')",
            )
        }
    }

    private fun readColumns(db: SupportSQLiteDatabase, table: String): Map<String, String> {
        val columns = mutableMapOf<String, String>()
        db.query("PRAGMA table_info(`" + table + "`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIndex)] = cursor.getString(typeIndex)
            }
        }
        return columns
    }

    private companion object {
        const val DB_NAME = "migration-2-3-test.db"

        /** v2 建库脚本：与 v1 唯一差异是 reminders 已含 weeks 列。 */
        val V2_DDL = listOf(
            "CREATE TABLE IF NOT EXISTS `semesters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `start_date` INTEGER NOT NULL, `major` TEXT NOT NULL, " +
                "`is_active` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `timetables` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`table_name` TEXT NOT NULL, `start_date` INTEGER NOT NULL, `semester_id` INTEGER, " +
                "FOREIGN KEY(`semester_id`) REFERENCES `semesters`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_timetables_semester_id` ON `timetables` (`semester_id`)",
            "CREATE TABLE IF NOT EXISTS `courses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timetable_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `teacher` TEXT NOT NULL, " +
                "`location` TEXT NOT NULL, `weekday` INTEGER NOT NULL, `start_section` INTEGER NOT NULL, " +
                "`end_section` INTEGER NOT NULL, `weeks` TEXT NOT NULL, " +
                "FOREIGN KEY(`timetable_id`) REFERENCES `timetables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_courses_timetable_id` ON `courses` (`timetable_id`)",
            "CREATE INDEX IF NOT EXISTS `index_courses_timetable_id_weekday` ON `courses` (`timetable_id`, `weekday`)",
            "CREATE TABLE IF NOT EXISTS `time_slots` (`timetable_id` INTEGER NOT NULL, `number` INTEGER NOT NULL, " +
                "`start_time` TEXT NOT NULL, `end_time` TEXT NOT NULL, PRIMARY KEY(`timetable_id`, `number`), " +
                "FOREIGN KEY(`timetable_id`) REFERENCES `timetables`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_time_slots_timetable_id` ON `time_slots` (`timetable_id`)",
            "CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`course_id` INTEGER NOT NULL, `advance_minutes` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, " +
                "`weeks` TEXT NOT NULL, " +
                "FOREIGN KEY(`course_id`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_reminders_course_id` ON `reminders` (`course_id`)",
            "CREATE TABLE IF NOT EXISTS `grades` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`semester_id` INTEGER NOT NULL, `course_name` TEXT NOT NULL, `credit` REAL NOT NULL, " +
                "`score` REAL NOT NULL, `grade_point` REAL)",
            "CREATE INDEX IF NOT EXISTS `index_grades_semester_id` ON `grades` (`semester_id`)",
            "CREATE TABLE IF NOT EXISTS `gpa_snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`semester_id` INTEGER NOT NULL, `gpa` REAL NOT NULL, `total_credits` REAL NOT NULL, " +
                "`course_count` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_gpa_snapshots_semester_id` ON `gpa_snapshots` (`semester_id`)",
        )
    }
}
