package com.example.timetable.data.adapter.json

import com.example.timetable.testing.FakeTimeProvider
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JsonTimetableParserTest {

    private lateinit var parser: JsonTimetableParser
    private lateinit var timeProvider: FakeTimeProvider

    @Before
    fun setUp() {
        timeProvider = FakeTimeProvider(today = LocalDate.of(2026, 9, 7))
        parser = JsonTimetableParser(
            json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
                explicitNulls = false
            },
            timeProvider = timeProvider,
        )
    }

    @Test
    fun parsesSamplePayloadWithFullFields() {
        val timetable = parser.parse(FULL_PAYLOAD)

        assertEquals("2026 秋 计算机", timetable.tableName)
        assertEquals(LocalDate.of(2026, 9, 7), timetable.startDate)
        assertEquals(2, timetable.timeSlots.size)
        assertEquals(1, timetable.courses.size)
        val course = timetable.courses.first()
        assertEquals("数据结构", course.name)
        assertEquals(2, course.weekday)
        assertEquals(1, course.startSection)
        assertEquals(2, course.endSection)
        assertEquals(listOf(1, 2, 3), course.weeks)
    }

    @Test
    fun missingWeeksUsesMaxWeekOfOtherCourses() {
        val payload = timetableWithCourses(
            """{ "name": "A", "weekday": 1, "startSection": 1, "endSection": 2, "weeks": [1, 5, 10] }""",
            """{ "name": "B", "weekday": 2, "startSection": 1, "endSection": 2 }""",
        )

        val courses = parser.parse(payload).courses
        assertEquals((1..10).toList(), courses[1].weeks)
    }

    @Test
    fun missingWeeksForAllCoursesFallsBackToTwentyWeeks() {
        val payload = timetableWithCourses(
            """{ "name": "A", "weekday": 1, "startSection": 1, "endSection": 2 }""",
            """{ "name": "B", "weekday": 2, "startSection": 1, "endSection": 2 }""",
        )

        val courses = parser.parse(payload).courses
        assertEquals((1..20).toList(), courses[0].weeks)
        assertEquals((1..20).toList(), courses[1].weeks)
    }

    @Test
    fun emptyWeeksArrayMeansNoSchedule() {
        val payload = timetableWithCourses(
            """{ "name": "A", "weekday": 1, "startSection": 1, "endSection": 2, "weeks": [] }""",
            """{ "name": "B", "weekday": 2, "startSection": 1, "endSection": 2, "weeks": [3] }""",
        )

        val courses = parser.parse(payload).courses
        assertTrue("空数组应保持为空，不得兜底", courses[0].weeks.isEmpty())
        assertEquals(listOf(3), courses[1].weeks)
    }

    @Test
    fun missingWeekdayThrowsMissingFieldWithPath() {
        val payload = timetableWithCourses(
            """{ "name": "A", "startSection": 1, "endSection": 2, "weeks": [1] }""",
        )

        val error = assertThrows(CourseParseException::class.java) { parser.parse(payload) }
        assertEquals("courses[0].weekday", error.field)
    }

    @Test
    fun blankCourseNameThrowsMissingFieldWithPath() {
        val payload = timetableWithCourses(
            """{ "name": "   ", "weekday": 1, "startSection": 1, "endSection": 2 }""",
        )

        val error = assertThrows(CourseParseException::class.java) { parser.parse(payload) }
        assertEquals("courses[0].name", error.field)
    }

    @Test
    fun missingStartSectionThrowsMissingFieldWithPath() {
        val payload = timetableWithCourses(
            """{ "name": "A", "weekday": 1, "endSection": 2 }""",
        )

        val error = assertThrows(CourseParseException::class.java) { parser.parse(payload) }
        assertEquals("courses[0].startSection", error.field)
    }

    @Test
    fun endSectionBeforeStartSectionIsSchemaErrorWithPathInMessage() {
        val payload = timetableWithCourses(
            """{ "name": "A", "weekday": 1, "startSection": 3, "endSection": 1 }""",
        )

        val error = assertThrows(CourseParseException::class.java) { parser.parse(payload) }
        assertNull("值非法不得伪装成字段缺失", error.field)
        assertTrue(error.message.orEmpty().contains("courses[0].endSection"))
    }

    @Test
    fun weekdayOutOfRangeIsSchemaError() {
        val payload = timetableWithCourses(
            """{ "name": "A", "weekday": 9, "startSection": 1, "endSection": 2 }""",
        )

        val error = assertThrows(CourseParseException::class.java) { parser.parse(payload) }
        assertNull(error.field)
        assertTrue(error.message.orEmpty().contains("courses[0].weekday"))
    }

    @Test
    fun endSectionDefaultsToStartSection() {
        val payload = timetableWithCourses(
            """{ "name": "A", "weekday": 1, "startSection": 4 }""",
        )

        val course = parser.parse(payload).courses.first()
        assertEquals(4, course.startSection)
        assertEquals(4, course.endSection)
    }

    @Test
    fun malformedJsonIsSchemaError() {
        val error = assertThrows(CourseParseException::class.java) { parser.parse("{ not json") }
        assertNull(error.field)
        assertNotNull(error.cause)
    }

    @Test
    fun topLevelArrayIsSchemaError() {
        val error = assertThrows(CourseParseException::class.java) { parser.parse("[1, 2, 3]") }
        assertNull(error.field)
    }

    @Test
    fun blankPayloadIsSchemaError() {
        val error = assertThrows(CourseParseException::class.java) { parser.parse("   ") }
        assertNull(error.field)
    }

    @Test
    fun singleDigitTimeIsNormalized() {
        val payload = """
            {
              "timeSlots": [ { "number": 1, "startTime": "8:00", "endTime": "9:5" } ],
              "courses": []
            }
        """.trimIndent()

        val slots = parser.parse(payload).timeSlots
        assertEquals("08:00", slots[0].startTime)
        assertEquals("09:05", slots[0].endTime)
        assertTrue(slots[0].isValid)
    }

    @Test
    fun unparsableTimeIsKeptWithoutAbortingImport() {
        val payload = """
            {
              "timeSlots": [ { "number": 1, "startTime": "上午", "endTime": "25:99" } ],
              "courses": [ { "name": "A", "weekday": 1, "startSection": 1, "endSection": 2 } ]
            }
        """.trimIndent()

        val timetable = parser.parse(payload)
        assertEquals("上午", timetable.timeSlots[0].startTime)
        assertEquals("25:99", timetable.timeSlots[0].endTime)
        assertTrue(!timetable.timeSlots[0].isValid)
        assertEquals(1, timetable.courses.size)
    }

    @Test
    fun missingTimeSlotNumberIsMissingField() {
        val payload = """
            { "timeSlots": [ { "startTime": "08:00", "endTime": "08:45" } ], "courses": [] }
        """.trimIndent()

        val error = assertThrows(CourseParseException::class.java) { parser.parse(payload) }
        assertEquals("timeSlots[0].number", error.field)
    }

    @Test
    fun missingStartDateFallsBackToProviderToday() {
        val payload = """{ "courses": [] }"""
        assertEquals(timeProvider.today(), parser.parse(payload).startDate)
    }

    @Test
    fun missingTableNameFallsBackToDefault() {
        val payload = """{ "courses": [] }"""
        assertEquals("我的课表", parser.parse(payload).tableName)
    }

    @Test
    fun slashSeparatedStartDateIsAccepted() {
        val payload = """{ "startDate": "2026/09/07", "courses": [] }"""
        assertEquals(LocalDate.of(2026, 9, 7), parser.parse(payload).startDate)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val payload = """
            { "tableName": "T", "unknownTop": 1, "courses": [
              { "name": "A", "weekday": 1, "startSection": 1, "endSection": 2, "unknownField": "x" }
            ] }
        """.trimIndent()

        assertEquals(1, parser.parse(payload).courses.size)
    }

    private fun timetableWithCourses(vararg courses: String): String =
        """{ "courses": [ """ + courses.joinToString(separator = ",") + """ ] }"""

    private companion object {
        val FULL_PAYLOAD = """
            {
              "tableName": "2026 秋 计算机",
              "startDate": "2026-09-07",
              "timeSlots": [
                { "number": 1, "startTime": "08:00", "endTime": "08:45" },
                { "number": 2, "startTime": "08:55", "endTime": "09:40" }
              ],
              "courses": [
                {
                  "name": "数据结构",
                  "teacher": "王强",
                  "location": "计算机楼 402",
                  "weekday": 2,
                  "startSection": 1,
                  "endSection": 2,
                  "weeks": [1, 2, 3]
                }
              ]
            }
        """.trimIndent()
    }
}
