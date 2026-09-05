package com.example.mntnode.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.mntnode.R
import kotlin.math.roundToInt

/**
 * Global EPUB typography, spacing, theme profiles, and page-turn (PDF + EPUB).
 * Embedded in each reader’s theme (palette) dialog—not in the app drawer.
 */
@Composable
fun ReaderGlobalThemePreferences(
    readerOptionsPrefs: SharedPreferences,
    modifier: Modifier = Modifier,
) {
    val initialTypo = remember { loadReaderGlobalEpubTypography(readerOptionsPrefs) }
    var typoBold by remember { mutableStateOf(initialTypo.bold) }
    var typoItalic by remember { mutableStateOf(initialTypo.italic) }
    var typoUnderline by remember { mutableStateOf(initialTypo.underline) }
    var typoShadow by remember { mutableStateOf(initialTypo.shadow) }
    var paraSpacingPct by remember {
        mutableStateOf(loadParagraphSpacingPercent(readerOptionsPrefs))
    }
    var lineSpacingStep by remember {
        mutableStateOf(loadLineSpacingStep(readerOptionsPrefs))
    }
    var showSaveThemeDialog by remember { mutableStateOf(false) }
    var showLoadThemeDialog by remember { mutableStateOf(false) }
    var saveThemeName by remember { mutableStateOf("") }
    val loadThemeScroll = rememberScrollState()
    var bookmarkColorArgb by remember { mutableIntStateOf(loadBookmarkIconColorArgb(readerOptionsPrefs)) }
    var showBookmarkColorPicker by remember { mutableStateOf(false) }

    Column(modifier) {
        Text(
            text = stringResource(R.string.settings_epub_typography),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_epub_typography_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        ReaderPrefsSwitchRow(
            label = stringResource(R.string.settings_epub_typography_bold),
            checked = typoBold,
            onCheckedChange = { v ->
                typoBold = v
                readerOptionsPrefs.edit().putBoolean("epub_global_text_bold", v).apply()
            },
        )
        ReaderPrefsSwitchRow(
            label = stringResource(R.string.settings_epub_typography_italic),
            checked = typoItalic,
            onCheckedChange = { v ->
                typoItalic = v
                readerOptionsPrefs.edit().putBoolean("epub_global_text_italic", v).apply()
            },
        )
        ReaderPrefsSwitchRow(
            label = stringResource(R.string.settings_epub_typography_underline),
            checked = typoUnderline,
            onCheckedChange = { v ->
                typoUnderline = v
                readerOptionsPrefs.edit().putBoolean("epub_global_text_underline", v).apply()
            },
        )
        ReaderPrefsSwitchRow(
            label = stringResource(R.string.settings_epub_typography_shadow),
            checked = typoShadow,
            onCheckedChange = { v ->
                typoShadow = v
                readerOptionsPrefs.edit().putBoolean("epub_global_text_shadow", v).apply()
            },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(
            text = stringResource(R.string.settings_epub_paragraph_spacing),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_epub_paragraph_spacing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.settings_epub_paragraph_spacing_value, paraSpacingPct),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = paraSpacingPct.toFloat(),
            onValueChange = { v ->
                val p = v.roundToInt().coerceIn(0, 200)
                paraSpacingPct = p
                readerOptionsPrefs.edit()
                    .putInt(KEY_EPUB_GLOBAL_PARAGRAPH_SPACING_PERCENT, p)
                    .apply()
            },
            valueRange = 0f..200f,
            steps = 199,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_epub_line_spacing),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_epub_line_spacing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.settings_epub_line_spacing_value, lineSpacingStep),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = lineSpacingStep.toFloat(),
            onValueChange = { v ->
                val s = v.roundToInt().coerceIn(-5, 20)
                lineSpacingStep = s
                readerOptionsPrefs.edit()
                    .putInt(KEY_EPUB_GLOBAL_LINE_SPACING_STEP, s)
                    .apply()
            },
            valueRange = -5f..20f,
            steps = 24,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                typoBold = false
                typoItalic = false
                typoUnderline = false
                typoShadow = false
                paraSpacingPct = DEFAULT_PARAGRAPH_SPACING_PERCENT
                lineSpacingStep = DEFAULT_LINE_SPACING_STEP
                readerOptionsPrefs.edit()
                    .putBoolean("epub_global_text_bold", false)
                    .putBoolean("epub_global_text_italic", false)
                    .putBoolean("epub_global_text_underline", false)
                    .putBoolean("epub_global_text_shadow", false)
                    .putInt(KEY_EPUB_GLOBAL_PARAGRAPH_SPACING_PERCENT, DEFAULT_PARAGRAPH_SPACING_PERCENT)
                    .putInt(KEY_EPUB_GLOBAL_LINE_SPACING_STEP, DEFAULT_LINE_SPACING_STEP)
                    .apply()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.settings_reader_reset_defaults))
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text(
            text = stringResource(R.string.settings_bookmark_icon_color),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.settings_bookmark_icon_color_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        ThemeColorSwatch(
            colorArgb = bookmarkColorArgb,
            label = stringResource(R.string.settings_bookmark_icon_color),
            onClick = { showBookmarkColorPicker = true },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = {
                    saveThemeName = ""
                    showSaveThemeDialog = true
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.settings_reader_save_theme))
            }
            TextButton(
                onClick = { showLoadThemeDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.settings_reader_load_theme))
            }
        }
    }

    if (showSaveThemeDialog) {
        val globalPageViewDefault = readerOptionsPrefs.getBoolean("global_page_view_default", false)
        AlertDialog(
            onDismissRequest = { showSaveThemeDialog = false },
            title = { Text(stringResource(R.string.settings_reader_theme_save_title)) },
            text = {
                OutlinedTextField(
                    value = saveThemeName,
                    onValueChange = { saveThemeName = it },
                    label = { Text(stringResource(R.string.settings_reader_theme_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = saveThemeName.trim()
                        if (name.isNotEmpty()) {
                            val profile = ReaderThemeProfile(
                                name = name,
                                globalPageViewDefault = globalPageViewDefault,
                                bold = typoBold,
                                italic = typoItalic,
                                underline = typoUnderline,
                                shadow = typoShadow,
                                paragraphSpacingPercent = paraSpacingPct,
                                lineSpacingStep = lineSpacingStep,
                            )
                            upsertReaderThemeProfile(readerOptionsPrefs, profile)
                            showSaveThemeDialog = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveThemeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showLoadThemeDialog) {
        val savedProfiles = loadReaderThemeProfiles(readerOptionsPrefs)
        AlertDialog(
            onDismissRequest = { showLoadThemeDialog = false },
            title = { Text(stringResource(R.string.settings_reader_theme_load_title)) },
            text = {
                if (savedProfiles.isEmpty()) {
                    Text(stringResource(R.string.settings_reader_no_saved_themes))
                } else {
                    Column(
                        Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(loadThemeScroll),
                    ) {
                        savedProfiles.forEach { profile ->
                            TextButton(
                                onClick = {
                                    profile.applyToPrefs(readerOptionsPrefs)
                                    typoBold = profile.bold
                                    typoItalic = profile.italic
                                    typoUnderline = profile.underline
                                    typoShadow = profile.shadow
                                    paraSpacingPct = profile.paragraphSpacingPercent
                                    lineSpacingStep = profile.lineSpacingStep
                                    showLoadThemeDialog = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(profile.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadThemeDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
    if (showBookmarkColorPicker) {
        HsvColorPickerDialog(
            title = stringResource(R.string.settings_bookmark_icon_color),
            initialArgb = bookmarkColorArgb,
            onDismiss = { showBookmarkColorPicker = false },
            onConfirm = { argb ->
                bookmarkColorArgb = argb
                saveBookmarkIconColorArgb(readerOptionsPrefs, argb)
                showBookmarkColorPicker = false
            },
        )
    }
}

@Composable
private fun ReaderPrefsSwitchRow(
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
