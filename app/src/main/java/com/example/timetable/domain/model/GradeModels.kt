package com.example.timetable.domain.model

/**
 * 成绩记录与 GPA 汇总模型（A8：GPA 算法暂不实现，仅保留数据结构）。
 */
data class GradeRecord(
    val id: Long = 0L,
    val courseName: String,
    val credit: Double,
    val score: Double,
    val semesterId: Long = 0L,
    val gradePoint: Double? = null,
)

data class GpaSummary(
    val semesterId: Long,
    val gpa: Double,
    val totalCredits: Double,
    val courseCount: Int,
)
