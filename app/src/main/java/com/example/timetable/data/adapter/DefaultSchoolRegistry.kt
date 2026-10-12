package com.example.timetable.data.adapter

import com.example.timetable.domain.importer.SchoolAdapter
import com.example.timetable.domain.importer.SchoolRegistry
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 注册表实现：内置适配器由 Hilt @IntoSet 注入，导入链路只查表，无学校分支。
 */
@Singleton
class DefaultSchoolRegistry @Inject constructor(
    adapters: Set<@JvmSuppressWildcards SchoolAdapter>,
) : SchoolRegistry {

    private val adaptersById = ConcurrentHashMap<String, SchoolAdapter>()

    init {
        adapters.forEach { adapter -> adaptersById[adapter.schoolId] = adapter }
    }

    override fun register(adapter: SchoolAdapter) {
        adaptersById[adapter.schoolId] = adapter
    }

    override fun adapter(schoolId: String): SchoolAdapter? = adaptersById[schoolId]

    override fun adapters(): List<SchoolAdapter> = adaptersById.values.sortedBy { it.schoolId }
}
