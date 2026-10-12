package com.example.timetable.ui.theme

import java.time.LocalTime

/** 时间动态主题的四个时段。 */
enum class DayPeriod {
    MORNING,
    AFTERNOON,
    DUSK,
    NIGHT,
}

/**
 * 时段判定的纯函数。边界常量集中在这里，可被单测逐个点位覆盖。
 * 区间左闭右开：06:00 属晨间，10:59 仍属晨间，11:00 起属午后。
 */
object DayPeriodResolver {

    val MORNING_START: LocalTime = LocalTime.of(6, 0)
    val AFTERNOON_START: LocalTime = LocalTime.of(11, 0)
    val DUSK_START: LocalTime = LocalTime.of(17, 0)
    val NIGHT_START: LocalTime = LocalTime.of(20, 0)

    fun resolve(time: LocalTime): DayPeriod = when {
        time < MORNING_START -> DayPeriod.NIGHT
        time < AFTERNOON_START -> DayPeriod.MORNING
        time < DUSK_START -> DayPeriod.AFTERNOON
        time < NIGHT_START -> DayPeriod.DUSK
        else -> DayPeriod.NIGHT
    }
}
