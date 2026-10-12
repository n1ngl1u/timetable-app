package com.example.timetable.ui.timetable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timetable.R

/** 导入入口面板：文件 / 剪贴板 / 手动粘贴三条路径。导入进行中三个入口与按钮全部禁用。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    manualPayload: String,
    importing: Boolean,
    onManualPayloadChanged: (String) -> Unit,
    onPickFile: () -> Unit,
    onReadClipboard: () -> Unit,
    onSubmitManual: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(id = R.string.import_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                onClick = onPickFile,
                enabled = !importing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(id = R.string.import_from_file))
            }
            HorizontalDivider()
            TextButton(
                onClick = onReadClipboard,
                enabled = !importing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(id = R.string.import_from_clipboard))
            }
            HorizontalDivider()
            Text(
                text = stringResource(id = R.string.import_manual_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = manualPayload,
                onValueChange = onManualPayloadChanged,
                enabled = !importing,
                minLines = MANUAL_MIN_LINES,
                maxLines = MANUAL_MAX_LINES,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onSubmitManual,
                enabled = !importing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(text = stringResource(id = R.string.import_manual_submit))
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 覆盖二次确认。仅在已有活跃课表且新载荷解析成功时出现。 */
@Composable
fun ImportOverwriteDialog(
    existingCourseCount: Int,
    newCourseCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.import_overwrite_title)) },
        text = {
            Text(
                text = stringResource(
                    id = R.string.import_overwrite_message,
                    existingCourseCount,
                    newCourseCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(id = R.string.import_overwrite_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.import_overwrite_cancel))
            }
        },
    )
}

private const val MANUAL_MIN_LINES = 6
private const val MANUAL_MAX_LINES = 12
