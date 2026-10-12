package com.example.timetable.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.timetable.data.local.db.AppDatabase
import com.example.timetable.data.local.db.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v1 -> v2 迁移：reminders 增加 weeks 列，老数据取默认空串。
 * v1 建库脚本按 Room 生成的 v2 schema 反推（唯一差异是 reminders 少 weeks 列），
 * 保证 TableInfo 校验能通过。
 */
@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migrationAddsWeeksColumnWithEmptyDefault() {
        context.deleteDatabase(DB_NAME)
        createVersion1Database()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            val sqlite = database.openHelper.writableDatabase

            val columns = readColumns(sqlite, "reminders")
            assertTrue("迁移后必须存在 weeks 列: " + columns.keys, columns.containsKey("weeks"))
            assertEquals("TEXT", columns["weeks"]?.first)
            assertEquals("weeks 必须 NOT NULL", 1, columns["weeks"]?.second)

            sqlite.query("SELECT weeks FROM reminders WHERE id = 1").use { cursor ->
                assertTrue("示例提醒应仍在", cursor.moveToFirst())
                assertEquals("老数据默认空串", "", cursor.getString(0))
            }

            assertEquals("数据库版本应升到 2", 2, sqlite.version)
        } finally {
            database.close()
            context.deleteDatabase(DB_NAME)
        }
    }

    private fun createVersion1Database() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        V1_DDL.forEach { statement -> db.execSQL(statement) }
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
                "INSERT INTO reminders (id, course_id, advance_minutes, enabled) VALUES (1, 1, 15, 1)",
            )
        }
    }

    private fun readColumns(db: SupportSQLiteDatabase, table: String): Map<String, Pair<String, Int>> {
        val columns = mutableMapOf<String, Pair<String, Int>>()
        db.query("PRAGMA table_info(`" + table + "`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val typeIndex = cursor.getColumnIndex("type")
            val notNullIndex = cursor.getColumnIndex("notnull")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIndex)] = cursor.getString(typeIndex) to cursor.getInt(notNullIndex)
            }
        }
        return columns
    }

    private companion object {
        const val DB_NAME = "migration-1-2-test.db"

        /** Room v2 schema 反推出的 v1 建库脚本：reminders 不含 weeks 列。 */
        val V1_DDL = listOf(
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
