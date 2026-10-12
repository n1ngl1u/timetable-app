package com.example.timetable.domain.importer

/**
 * schoolId -> 适配器注册表，导入链路的唯一分发点，禁止 if-else 学校分支。
 */
interface SchoolRegistry {

    fun register(adapter: SchoolAdapter)

    fun adapter(schoolId: String): SchoolAdapter?

    fun adapters(): List<SchoolAdapter>
}
