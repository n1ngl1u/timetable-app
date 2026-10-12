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
import com.example.timetable.domain.model.AppSettings
import com.example.timetable.domain.repository.SettingsRepository
import com.example.timetable.domain.usecase.ImportTimetableUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val importTimetableUseCase: ImportTimetableUseCase,
    private val perfTracer: PerfTracer,
) : BaseViewModel() {

    private val mutableState = MutableStateFlow<UiState<AppSettings>>(UiState.Loading)

    val state: StateFlow<UiState<AppSettings>> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                mutableState.value = UiState.Success(settings)
            }
        }
    }

    fun importPayload(payload: String) {
        viewModelScope.launch {
            val result = importTimetableUseCase(
                ImportRequest(
                    schoolId = LocalFileSchoolAdapter.SCHOOL_ID,
                    payload = payload,
                ),
            )
            when (result) {
                is ImportResult.Success -> sendEvent(
                    UiEvent.ShowMessage("导入成功：" + result.courseCount + " 门课程"),
                )
                is ImportResult.SessionExpired -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.NetworkFailure -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.SchemaChanged -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.MissingField -> sendEvent(UiEvent.ShowMessage(result.message))
                is ImportResult.InvalidInput -> sendEvent(UiEvent.ShowMessage(result.message))
            }
        }
    }

    /** UI 首次渲染出数据后收口导入耗时打点（T0 -> 首帧）。 */
    fun onStateRendered() {
        perfTracer.end(TraceTags.IMPORT_FIRST_FRAME)
    }
}
