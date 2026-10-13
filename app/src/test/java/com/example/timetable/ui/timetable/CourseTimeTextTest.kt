package com.example.timetable.ui.timetable

import com.example.timetable.domain.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseTimeTextTest {

    @Test
    fun mappedSectionsShowBothRangeAndTime() {
        assertEquals(
            "第 1-2 节（08:00-09:40）",
            CourseTimeText.of(TWO_SLOTS, startSection = 1, endSection = 2),
        )
    }

    @Test
    fun emptyTimeSlotsFallBackToSectionRange() {
        assertEquals(
            "第 1-2 节",
            CourseTimeText.of(emptyList(), startSection = 1, endSection = 2),
        )
    }

    @Test
    fun missingSectionInTimeSlotsFallsBackToSectionRange() {
        assertEquals(
            "第 5-6 节",
            CourseTimeText.of(TWO_SLOTS, startSection = 5, endSection = 6),
        )
    }

    @Test
    fun singleSectionCourseShowsSingleSectionAndTime() {
        assertEquals(
            "第 3 节（10:00-10:45）",
            CourseTimeText.of(
                listOf(TimeSlot(number = 3, startTime = "10:00", endTime = "10:45")),
                startSection = 3,
                endSection = 3,
            ),
        )
    }

    @Test
    fun singleSectionWithoutMappingShowsSingleSection() {
        assertEquals(
            "第 3 节",
            CourseTimeText.of(emptyList(), startSection = 3, endSection = 3),
        )
    }

    /** 只有 start 有映射时也必须整体回退，不能显示成 "08:00-"。 */
    @Test
    fun partialMappingFallsBackEntirely() {
        val onlyStart = listOf(TimeSlot(number = 1, startTime = "08:00", endTime = "08:45"))

        assertEquals("第 1-2 节", CourseTimeText.of(onlyStart, startSection = 1, endSection = 2))
    }

    @Test
    fun blankTimeStringsFallBackToSectionRange() {
        val blankSlots = listOf(
            TimeSlot(number = 1, startTime = "", endTime = ""),
            TimeSlot(number = 2, startTime = "  ", endTime = "  "),
        )

        assertEquals("第 1-2 节", CourseTimeText.of(blankSlots, startSection = 1, endSection = 2))
    }

    private companion object {
        val TWO_SLOTS = listOf(
            TimeSlot(number = 1, startTime = "08:00", endTime = "08:45"),
            TimeSlot(number = 2, startTime = "08:55", endTime = "09:40"),
        )
    }
}
