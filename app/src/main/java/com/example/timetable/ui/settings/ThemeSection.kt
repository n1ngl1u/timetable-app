package com.example.timetable.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.timetable.R
import com.example.timetable.ui.theme.DarkModePreference
import com.example.timetable.ui.theme.ThemeId

/** 设置页第一个分区：主题三选一 + 深浅色三态。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSection(
    themeId: ThemeId,
    darkModePreference: DarkModePreference,
    onThemeSelected: (ThemeId) -> Unit,
    onDarkModePreferenceChange: (DarkModePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(id = R.string.settings_theme_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeId.entries.forEach { candidate ->
                FilterChip(
                    selected = candidate == themeId,
                    onClick = { onThemeSelected(candidate) },
                    label = { Text(text = stringResource(id = candidate.labelRes())) },
                )
            }
        }
        Text(
            text = stringResource(id = R.string.settings_dark_mode),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            DarkModePreference.entries.forEachIndexed { index, preference ->
                SegmentedButton(
                    selected = preference == darkModePreference,
                    onClick = { onDarkModePreferenceChange(preference) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = DarkModePreference.entries.size,
                    ),
                    label = { Text(text = stringResource(id = preference.labelRes())) },
                )
            }
        }
    }
}

private fun ThemeId.labelRes(): Int = when (this) {
    ThemeId.DEFAULT -> R.string.settings_theme_default
    ThemeId.TIME_OF_DAY -> R.string.settings_theme_time_of_day
    ThemeId.LIQUID_GLASS -> R.string.settings_theme_liquid_glass
}

private fun DarkModePreference.labelRes(): Int = when (this) {
    DarkModePreference.FOLLOW_SYSTEM -> R.string.settings_dark_mode_follow
    DarkModePreference.LIGHT -> R.string.settings_dark_mode_light
    DarkModePreference.DARK -> R.string.settings_dark_mode_dark
}
