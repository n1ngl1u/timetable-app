package com.example.timetable.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timetable.R
import com.example.timetable.core.ui.UiEvent
import com.example.timetable.core.ui.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val model = (state as? UiState.Success<*>)?.data as? SettingsUiModel

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onPermissionStateMaybeChanged() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                is UiEvent.RequestCalendarPermission -> calendarPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_CALENDAR,
                        android.Manifest.permission.WRITE_CALENDAR,
                    ),
                )
                is UiEvent.OpenSystemSettings -> {
                    context.startActivity(
                        Intent(event.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                is UiEvent.Navigate -> Unit
                is UiEvent.RequestNotificationPermission -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(text = stringResource(id = R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = stringResource(id = R.string.settings_calendar_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.settings_calendar_sync),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(id = R.string.settings_calendar_sync_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = model?.calendarSyncEnabled ?: false,
                    onCheckedChange = viewModel::onCalendarSyncToggle,
                )
            }
            if (model?.showCalendarPermissionHint == true) {
                Text(
                    text = stringResource(id = R.string.settings_calendar_permission_required),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (model?.calendarSyncEnabled == true && model.calendarPermissionGranted.not()) {
                TextButton(
                    onClick = {
                        viewModel.onPermissionStateMaybeChanged()
                        calendarPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.READ_CALENDAR,
                                android.Manifest.permission.WRITE_CALENDAR,
                            ),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(text = stringResource(id = R.string.settings_calendar_open_settings))
                }
            }
            val summary = model?.lastSyncSummary
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Text(
                text = stringResource(id = R.string.settings_other_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                text = stringResource(id = R.string.settings_other_placeholder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
