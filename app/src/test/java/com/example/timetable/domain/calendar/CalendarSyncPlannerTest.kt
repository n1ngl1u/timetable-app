package com.example.timetable.domain.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSyncPlannerTest {

    @Test
    fun missingLinkProducesInsert() {
        val spec = spec(segmentStartWeek = 1, segmentEndWeek = 16)

        val plan = CalendarSyncPlanner.plan(
            mappings = listOf(CourseEventMapping.Mapped(courseId = 1L, specs = listOf(spec))),
            existingLinks = emptyList(),
        )

        assertEquals(1, plan.inserts.size)
        assertEquals(1L, plan.inserts.single().courseId)
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun identicalHashProducesUnchanged() {
        val spec = spec(segmentStartWeek = 1, segmentEndWeek = 16)
        val link = link(spec = spec, eventId = 100L)

        val plan = CalendarSyncPlanner.plan(
            mappings = listOf(CourseEventMapping.Mapped(courseId = 1L, specs = listOf(spec))),
            existingLinks = listOf(link),
        )

        assertTrue("内容一致不应重写系统日历", plan.inserts.isEmpty())
        assertTrue(plan.updates.isEmpty())
        assertTrue(plan.deletes.isEmpty())
        assertEquals(1, plan.unchanged.size)
    }

    @Test
    fun changedHashProducesUpdate() {
        val existing = link(spec = spec(segmentStartWeek = 1, segmentEndWeek = 16, hash = "old"), eventId = 100L)
        val desired = spec(segmentStartWeek = 1, segmentEndWeek = 16, hash = "new")

        val plan = CalendarSyncPlanner.plan(
            mappings = listOf(CourseEventMapping.Mapped(courseId = 1L, specs = listOf(desired))),
            existingLinks = listOf(existing),
        )

        assertEquals(1, plan.updates.size)
        assertEquals(100L, plan.updates.single().calendarEventId)
        assertTrue(plan.inserts.isEmpty())
    }

    @Test
    fun shrunkWeeksProduceDelete() {
        val existing = link(spec = spec(segmentStartWeek = 5, segmentEndWeek = 8), eventId = 200L)
        val desired = spec(segmentStartWeek = 1, segmentEndWeek = 4)

        val plan = CalendarSyncPlanner.plan(
            mappings = listOf(CourseEventMapping.Mapped(courseId = 1L, specs = listOf(desired))),
            existingLinks = listOf(existing),
        )

        assertEquals(1, plan.inserts.size)
        assertEquals(1, plan.deletes.size)
        assertEquals(200L, plan.deletes.single().calendarEventId)
    }

    @Test
    fun skippedCourseDeletesItsExistingEvents() {
        val existing = link(spec = spec(segmentStartWeek = 1, segmentEndWeek = 16), eventId = 300L)

        val plan = CalendarSyncPlanner.plan(
            mappings = listOf(
                CourseEventMapping.Skipped(1L, "数据结构", CalendarSkipReason.MISSING_TIME_SLOT),
            ),
            existingLinks = listOf(existing),
        )

        assertEquals(1, plan.skippedCourses.size)
        assertEquals(
            "时间映射消失后旧事件必须清掉，否则留下错时间的事件",
            1,
            plan.deletes.size,
        )
        assertEquals(300L, plan.deletes.single().calendarEventId)
    }

    @Test
    fun coursesAreIsolatedFromEachOther() {
        val courseOneSpec = spec(segmentStartWeek = 1, segmentEndWeek = 4, hash = "a")
        val courseTwoSpec = spec(segmentStartWeek = 1, segmentEndWeek = 4, hash = "b")

        val plan = CalendarSyncPlanner.plan(
            mappings = listOf(
                CourseEventMapping.Mapped(courseId = 1L, specs = listOf(courseOneSpec)),
                CourseEventMapping.Mapped(courseId = 2L, specs = listOf(courseTwoSpec)),
            ),
            existingLinks = listOf(link(spec = courseOneSpec, eventId = 1L, courseId = 1L)),
        )

        assertEquals(1, plan.unchanged.size)
        assertEquals(1, plan.inserts.size)
        assertEquals(2L, plan.inserts.single().courseId)
        assertTrue(plan.deletes.isEmpty())
    }

    @Test
    fun emptyInputProducesEmptyPlan() {
        val plan = CalendarSyncPlanner.plan(emptyList(), emptyList())

        assertTrue(plan.isEmpty)
        assertEquals(0, plan.totalChanges)
    }

    private fun spec(
        segmentStartWeek: Int,
        segmentEndWeek: Int,
        hash: String = "hash-" + segmentStartWeek + "-" + segmentEndWeek,
    ) = CalendarEventSpec(
        segmentStartWeek = segmentStartWeek,
        segmentEndWeek = segmentEndWeek,
        title = "数据结构",
        location = "计算机楼 402",
        description = "王强 / 计算机楼 402",
        startMillis = 1_787_000_000_000L,
        endMillis = 1_787_006_000_000L,
        timeZone = "UTC",
        rrule = "FREQ=WEEKLY;BYDAY=MO;COUNT=" + (segmentEndWeek - segmentStartWeek + 1),
        contentHash = hash,
    )

    private fun link(
        spec: CalendarEventSpec,
        eventId: Long,
        courseId: Long = 1L,
    ) = CalendarEventLink(
        courseId = courseId,
        calendarEventId = eventId,
        segmentStartWeek = spec.segmentStartWeek,
        segmentEndWeek = spec.segmentEndWeek,
        contentHash = spec.contentHash,
        syncedAt = 0L,
    )
}
