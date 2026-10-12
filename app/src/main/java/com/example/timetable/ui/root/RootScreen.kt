package com.example.timetable.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timetable.R
import com.example.timetable.core.ui.UiEvent
import com.example.timetable.core.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    autoImportPayload: String?,
    modifier: Modifier = Modifier,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val model = (state as? UiState.Success<*>)?.data as? RootUiModel

    LaunchedEffect(autoImportPayload) {
        if (!autoImportPayload.isNullOrBlank()) {
            viewModel.importPayload(autoImportPayload)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                is UiEvent.Navigate -> Unit
                is UiEvent.OpenSystemSettings -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    TimetableSwitcher(
                        options = model?.timetables.orEmpty(),
                        activeTimetableId = model?.activeTimetableId,
                        activeTimetableName = model?.activeTimetableName,
                        onSelected = viewModel::onTimetableSelected,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = state) {
                UiState.Idle, UiState.Loading -> CircularProgressIndicator()
                UiState.Empty -> Text(text = stringResource(id = R.string.root_empty))
                is UiState.Error -> Text(
                    text = stringResource(id = R.string.root_error) + ": " + current.error.message,
                    color = MaterialTheme.colorScheme.error,
                )
                is UiState.Success<*> -> Text(text = summaryOf(current.data as? RootUiModel))
            }
        }
    }
}

/** 正文摘要严格一行文本，不引入卡片 / 列表 / 分组标题。 */
@Composable
private fun summaryOf(model: RootUiModel?): String =
    if (model != null && model.courseCount > 0) {
        stringResource(id = R.string.root_course_count, model.courseCount)
    } else {
        stringResource(id = R.string.root_empty)
    }

/** 课表切换入口：下拉列表，不做完整管理页，也不含删除。 */
@Composable
private fun TimetableSwitcher(
    options: List<TimetableOption>,
    activeTimetableId: Long?,
    activeTimetableName: String?,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = options.isNotEmpty(),
        ) {
            Text(
                text = activeTimetableName ?: stringResource(id = R.string.root_switch_none),
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (option.id == activeTimetableId) {
                                option.name + " · " + stringResource(id = R.string.root_switch_current)
                            } else {
                                option.name
                            },
                            maxLines = 1,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option.id)
                    },
                )
            }
        }
    }
}
