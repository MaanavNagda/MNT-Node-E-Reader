package com.example.optireader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.optireader.R

@Composable
fun ReaderControlsSettingsSection(
    settings: ReaderControlsSettings,
    onSettingsChange: (ReaderControlsSettings) -> Unit,
) {
    Text(
        text = stringResource(R.string.reader_controls_interaction),
        style = MaterialTheme.typography.titleSmall,
    )
    ReaderControlsSwitchRow(
        label = stringResource(R.string.reader_controls_right_slide_volume),
        checked = settings.rightSlideVolume,
        onCheckedChange = { onSettingsChange(settings.copy(rightSlideVolume = it)) },
    )
    ReaderControlsSwitchRow(
        label = stringResource(R.string.reader_controls_left_slide_brightness),
        checked = settings.leftSlideBrightness,
        onCheckedChange = { onSettingsChange(settings.copy(leftSlideBrightness = it)) },
    )
    ReaderControlsSwitchRow(
        label = stringResource(R.string.reader_controls_volume_keys_page_turn),
        checked = settings.volumeKeysPageTurn,
        onCheckedChange = { onSettingsChange(settings.copy(volumeKeysPageTurn = it)) },
    )
    if (settings.volumeKeysPageTurn) {
        var expanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reader_controls_volume_key_forward),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { expanded = true }) {
                Text(
                    text = if (settings.volumeUpForward) {
                        stringResource(R.string.reader_controls_volume_up)
                    } else {
                        stringResource(R.string.reader_controls_volume_down)
                    },
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_controls_volume_up_controls_forward)) },
                    onClick = {
                        expanded = false
                        onSettingsChange(settings.copy(volumeUpForward = true))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_controls_volume_down_controls_forward)) },
                    onClick = {
                        expanded = false
                        onSettingsChange(settings.copy(volumeUpForward = false))
                    },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.reader_controls_volume_key_speed_ms,
                    settings.volumeKeyAnimationSpeedMs,
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
        Slider(
            value = settings.volumeKeyAnimationSpeedMs.toFloat(),
            onValueChange = {
                onSettingsChange(
                    settings.copy(
                        volumeKeyAnimationSpeedMs = it.toInt().coerceIn(
                            READER_VOLUME_KEY_ANIMATION_SPEED_MIN_MS,
                            READER_VOLUME_KEY_ANIMATION_SPEED_MAX_MS,
                        ),
                    ),
                )
            },
            valueRange = READER_VOLUME_KEY_ANIMATION_SPEED_MIN_MS.toFloat()..READER_VOLUME_KEY_ANIMATION_SPEED_MAX_MS.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(
        text = stringResource(R.string.reader_controls_status_customization),
        style = MaterialTheme.typography.titleSmall,
    )
    ReaderControlsSwitchRow(
        label = stringResource(R.string.reader_controls_show_battery),
        checked = settings.showBattery,
        onCheckedChange = { onSettingsChange(settings.copy(showBattery = it)) },
    )
    ReaderControlsSwitchRow(
        label = stringResource(R.string.reader_controls_show_chapter_time_left),
        checked = settings.showChapterTimeLeft,
        onCheckedChange = { onSettingsChange(settings.copy(showChapterTimeLeft = it)) },
    )
    ReaderControlsSwitchRow(
        label = stringResource(R.string.reader_controls_show_time),
        checked = settings.showTime,
        onCheckedChange = { onSettingsChange(settings.copy(showTime = it)) },
    )
    ReaderControlsSwitchRow(
        label = stringResource(R.string.reader_controls_show_page_count),
        checked = settings.showPageCount,
        onCheckedChange = { onSettingsChange(settings.copy(showPageCount = it)) },
    )
    ReaderControlsSwitchRow(
        label = stringResource(R.string.reader_controls_show_percentage),
        checked = settings.showPercentage,
        onCheckedChange = { onSettingsChange(settings.copy(showPercentage = it)) },
    )
    ReaderControlsSwitchRow(
        label = stringResource(R.string.reader_controls_show_book_time_left),
        checked = settings.showBookTimeLeft,
        onCheckedChange = { onSettingsChange(settings.copy(showBookTimeLeft = it)) },
    )
}

@Composable
private fun ReaderControlsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

