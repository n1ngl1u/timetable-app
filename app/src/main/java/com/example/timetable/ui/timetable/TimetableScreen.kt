package com.example.timetable.ui.timetable

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timetable.R
import com.example.timetable.core.ui.UiEvent
import com.example.timetable.core.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    autoImportPayload: String?,
    modifier: Modifier = Modifier,
    viewModel: TimetableViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val model = (state as? UiState.Success<*>)?.data as? TimetableUiModel
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.onPermissionStateMaybeChanged() }

    // MIME 不限：JSON 在不同 provider 上会报 application/json / text/plain / octet-stream，
    // 限定类型会让用户的文件在选择器里变灰。格式校验交给解析器，它给出的错误更准确。
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.onFilePicked(it.toString()) } }

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
                is UiEvent.RequestCalendarPermission -> Unit
                is UiEvent.OpenSystemSettings -> {
                    val intent = Intent(event.action).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (event.action == Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    }
                    context.startActivity(intent)
                }
                is UiEvent.RequestNotificationPermission -> notificationPermissionLauncher.launch(
                    android.Manifest.permission.POST_NOTIFICATIONS,
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = viewModel::onImportSheetOpened,
                            enabled = model?.importing != true,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_import),
                                contentDescription = stringResource(id = R.string.timetable_import),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TimetableSwitcher(
                            options = model?.timetables.orEmpty(),
                            activeTimetableId = model?.activeTimetableId,
                            activeTimetableName = model?.activeTimetableName,
                            onSelected = viewModel::onTimetableSelected,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = summaryOf(model),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (state is UiState.Loading || state is UiState.Idle) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                TimetableCourseList(
                    courses = model?.courses.orEmpty(),
                    onCourseLongPressed = viewModel::onCourseLongPressed,
                    onImportClick = viewModel::onImportSheetOpened,
                    modifier = Modifier.weight(1f),
                )
            }
            ReminderRow(
                model = model,
                onEnabledChange = viewModel::onRemindersEnabledChange,
                onAdvanceChange = viewModel::onReminderAdvanceChange,
            )
        }
    }

    val pendingDelete = model?.pendingDelete
    if (pendingDelete != null) {
        CourseDeleteDialog(
            course = pendingDelete,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed,
        )
    }

    if (model?.importSheetVisible == true) {
        ImportSheet(
            manualPayload = model.manualPayload,
            importing = model.importing,
            onManualPayloadChanged = viewModel::onManualPayloadChanged,
            onPickFile = {
                viewModel.onImportSheetClosed()
                filePickerLauncher.launch(arrayOf("*/*"))
            },
            onReadClipboard = viewModel::onImportFromClipboard,
            onSubmitManual = viewModel::onManualImportSubmitted,
            onDismiss = viewModel::onImportSheetClosed,
        )
    }

    val overwritePrompt = model?.overwritePrompt
    if (overwritePrompt != null) {
        ImportOverwriteDialog(
            existingCourseCount = overwritePrompt.existingCourseCount,
            newCourseCount = overwritePrompt.newCourseCount,
            onConfirm = viewModel::onOverwriteConfirmed,
            onDismiss = viewModel::onOverwriteDismissed,
        )
    }
}

/** 摘要严格一行文本，不引入卡片 / 列表 / 分组标题。 */
@Composable
private fun summaryOf(model: TimetableUiModel?): String =
    if (model != null && model.courseCount > 0) {
        stringResource(id = R.string.root_course_count, model.courseCount)
    } else {
        stringResource(id = R.string.root_empty)
    }

/** 提醒开关 + 提前量三档，单行控件；权限异常时只在必要时补一行提示。 */
@Composable
private fun ReminderRow(
    model: TimetableUiModel?,
    onEnabledChange: (Boolean) -> Unit,
    onAdvanceChange: (Int) -> Unit,
) {
    if (model == null) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.reminder_section_title),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.width(8.dp))
            model.advanceOptions.forEach { option ->
                FilterChip(
                    selected = option == model.reminderAdvanceMinutes,
                    onClick = { onAdvanceChange(option) },
                    label = { Text(text = option.toString()) },
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = model.remindersEnabled,
                onCheckedChange = onEnabledChange,
            )
        }
        if (model.showNotificationDisabledHint) {
            Text(
                text = stringResource(id = R.string.reminder_notifications_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (model.showExactAlarmDisabledHint) {
            Text(
                text = stringResource(id = R.string.reminder_exact_alarm_disabled),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

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
