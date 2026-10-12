package com.example.timetable.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timetable.R
import com.example.timetable.core.ui.UiState
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/** 以「初始月 ±600 月」为虚拟页码区间，避免无限 pager。 */
private const val PAGE_RANGE = 1201

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val model = (state as? UiState.Success<*>)?.data as? CalendarUiModel

    val anchorMonth = remember { viewModel.initialMonth }
    val anchorIndex = PAGE_RANGE / 2
    val pagerState = rememberPagerState(initialPage = anchorIndex) { PAGE_RANGE }

    // 月份唯一真源是 pager：翻页 -> ViewModel 保存；ViewModel 不对 pager 反向驱动
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.showMonth(anchorMonth.plusMonths((page - anchorIndex).toLong()))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.calendar_title)) },
                actions = {
                    val month = model?.month ?: anchorMonth
                    IconButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) {
                        Text(text = "\u2039", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = stringResource(
                            id = R.string.calendar_month_format,
                            month.year,
                            month.monthValue,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }) {
                        Text(text = "\u203A", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                UiState.Idle, UiState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                is UiState.Error -> Text(
                    text = stringResource(id = R.string.root_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> {
                    val current = model ?: return@Box
                    Column(modifier = Modifier.fillMaxSize()) {
                        WeekdayHeader()
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val month = anchorMonth.plusMonths((page - anchorIndex).toLong())
                            val counts = remember(month, current.index) {
                                current.index?.countsIn(month).orEmpty()
                            }
                            MonthPage(
                                month = month,
                                today = current.today,
                                counts = counts,
                                onSelectDate = viewModel::selectDate,
                            )
                        }
                    }
                }
            }
        }
    }

    val selectedDate = model?.selectedDate
    if (selectedDate != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) viewModel.dismissDayDetail()
                }
            },
            sheetState = sheetState,
        ) {
            DayDetailContent(
                date = selectedDate,
                courses = model.selectedDayCourses,
                modifier = Modifier.fillMaxHeight(0.6f),
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        WEEKDAY_LABELS.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthPage(
    month: YearMonth,
    today: LocalDate,
    counts: Map<LocalDate, Int>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val weeks = remember(month) { MonthGrid.weeksOf(month) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        inCurrentMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        courseCount = counts[date] ?: 0,
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    courseCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(CELL_HEIGHT)
            .padding(2.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isToday) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !inCurrentMonth -> MaterialTheme.colorScheme.outline
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            if (courseCount > 0) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(BADGE_SIZE)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun DayDetailContent(
    date: LocalDate,
    courses: List<DayCourseItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(text = date.toString(), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        if (courses.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.calendar_day_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // 本批点击课程不做任何跳转，只展示
            courses.forEach { course ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = course.timeText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(TIME_COLUMN_WIDTH),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = course.name, style = MaterialTheme.typography.bodyLarge)
                        if (course.location.isNotBlank()) {
                            Text(
                                text = course.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
private val CELL_HEIGHT = 56.dp
private val BADGE_SIZE = 6.dp
private val TIME_COLUMN_WIDTH = 96.dp
