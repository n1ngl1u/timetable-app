package com.example.timetable.ui.root

import androidx.lifecycle.viewModelScope
import com.example.timetable.core.perf.PerfTracer
import com.example.timetable.core.perf.TraceTags
import com.example.timetable.core.ui.BaseViewModel
import com.example.timetable.core.ui.UiEvent
import com.example.timetable.core.ui.UiState
import com.example.timetable.data.adapter.local.LocalFileSchoolAdapter
import com.example.timetable.domain.importer.ImportRequest
import com.example.timetable.domain.importer.ImportResult
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.repository.TimetableRepository
import com.example.timetable.domain.usecase.ImportTimetableUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 首页摘要模型：只喂一行文本，不做正式 UI。 */
data class RootUiModel(
    val pureModeEnabled: Boolean,
    val courseCount: Int,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val timetableRepository: TimetableRepository,
    private val importTimetableUseCase: ImportTimetableUseCase,
    private val perfTracer: PerfTracer,
) : BaseViewModel() {

    private val mutableState = MutableStateFlow<UiState<RootUiModel>>(UiState.Loading)

    val state: StateFlow<UiState<RootUiModel>> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                timetableRepository.observeTotalCourseCount(),
            ) { settings, courseCount ->
                RootUiModel(pureModeEnabled = settings.pureModeEnabled, courseCount = courseCount)
            }.collect { model -> mutableState.value = UiState.Success(model) }
        }
    }

    /**
     * 导出入口只服务基准测试与最小接线：T0 在此打点，T1 在课表数据落库后
     * 首次被数据流观察到时收口；失败时不收口，基准测试会因「未采集到打点」直接失败。
     */
    fun importPayload(payload: String) {
        viewModelScope.launch {
            perfTracer.mark(TraceTags.IMPORT_FIRST_FRAME)
            val result = importTimetableUseCase(
                ImportRequest(
                    schoolId = LocalFileSchoolAdapter.SCHOOL_ID,
                    payload = payload,
                ),
            )
            when (result) {
                is ImportResult.Success -> {
                    timetableRepository.observeTotalCourseCount().first { it > 0 }
                    perfTracer.end(TraceTags.IMPORT_FIRST_FRAME)
                    sendEvent(UiEvent.ShowMessage("导入成功：" + result.courseCount + " 门课程"))
                }
                is ImportResult.SessionExpired -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.NetworkFailure -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.SchemaChanged -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.MissingField -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.InvalidInput -> sendEvent(UiEvent.ShowMessage(result.message))
            }
        }
    }
}
