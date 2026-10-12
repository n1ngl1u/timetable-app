package com.example.timetable.ui.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
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

    LaunchedEffect(autoImportPayload) {
        if (!autoImportPayload.isNullOrBlank()) {
            viewModel.importPayload(autoImportPayload)
        }
    }

    LaunchedEffect(state) {
        if (state is UiState.Success<*>) {
            viewModel.onStateRendered()
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
        topBar = { TopAppBar(title = { Text(text = stringResource(id = R.string.app_name)) }) },
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
                is UiState.Success<*> -> Text(
                    text = current.data?.let { it.toString() } ?: stringResource(id = R.string.root_empty),
                )
            }
        }
    }
}
