package com.example.timetable.domain.usecase

import com.example.timetable.core.common.DispatcherProvider
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import com.example.timetable.domain.importer.ImportRequest
import com.example.timetable.domain.importer.ImportResult
import com.example.timetable.domain.repository.SchoolRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext

/**
 * 导入编排：解析与落库全部在 IO 协程完成，主线程零阻塞，并在入口打点。
 */
class ImportTimetableUseCase @Inject constructor(
    private val schoolRepository: SchoolRepository,
    private val perfTracer: PerfTracer,
    private val dispatchers: DispatcherProvider,
) {

    suspend operator fun invoke(request: ImportRequest): ImportResult = withContext(dispatchers.io) {
        perfTracer.mark(TraceTags.IMPORT_START)
        schoolRepository.importTimetable(request)
    }
}
