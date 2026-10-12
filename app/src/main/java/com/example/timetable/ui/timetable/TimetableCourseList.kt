package com.example.timetable.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timetable.R

/** 课程列表：长按行弹确认框删除。本批不做滑动删除（误触率高、无障碍差、测试脆）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimetableCourseList(
    courses: List<CourseRow>,
    onCourseLongPressed: (CourseRow) -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (courses.isEmpty()) {
        Column(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(id = R.string.timetable_courses_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onImportClick,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(text = stringResource(id = R.string.timetable_import))
            }
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items = courses, key = { course -> course.id }) { course ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onCourseLongPressed(course) },
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = course.weekdayLabel + " · " + course.sectionText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(text = course.name, style = MaterialTheme.typography.bodyLarge)
                }
                if (course.location.isNotBlank()) {
                    Text(
                        text = course.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun CourseDeleteDialog(
    course: CourseRow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.timetable_delete_title)) },
        text = {
            Column {
                Text(text = course.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(id = R.string.timetable_delete_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(id = R.string.timetable_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.timetable_delete_cancel))
            }
        },
    )
}
