package com.example.optireader.ui

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.optireader.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class GlobalEditorColorTarget { Text, Page, Highlight }

/**
 * Same content as the EPUB reader palette dialog (font, size, colors + global typography block).
 * [onConfirm] is invoked when the user taps OK with the edited [EpubThemeBaseline].
 */
@Composable
fun GlobalReaderBookThemeEditorDialog(
    initialBaseline: EpubThemeBaseline,
    readerOptionsPrefs: SharedPreferences,
    onDismiss: () -> Unit,
    onConfirm: (EpubThemeBaseline) -> Unit,
) {
    var theme by remember(initialBaseline) { mutableStateOf(initialBaseline) }
    var expanded by remember { mutableStateOf(false) }
    var fontNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var colorTarget by remember { mutableStateOf<GlobalEditorColorTarget?>(null) }
    LaunchedEffect(Unit) {
        fontNames = withContext(Dispatchers.Default) { SystemFontFamilies.loadCssFontFamilyNames() }
    }
    val scroll = rememberScrollState()

    when (colorTarget) {
        GlobalEditorColorTarget.Text ->
            HsvColorPickerDialog(
                title = stringResource(R.string.reader_color_text),
                initialArgb = theme.textArgb,
                onDismiss = { colorTarget = null },
                onConfirm = { argb ->
                    theme = theme.copy(textArgb = argb)
                    colorTarget = null
                },
            )
        GlobalEditorColorTarget.Page ->
            HsvColorPickerDialog(
                title = stringResource(R.string.reader_color_page),
                initialArgb = theme.pageArgb,
                onDismiss = { colorTarget = null },
                onConfirm = { argb ->
                    theme = theme.copy(pageArgb = argb)
                    colorTarget = null
                },
            )
        GlobalEditorColorTarget.Highlight ->
            HsvColorPickerDialog(
                title = stringResource(R.string.reader_color_highlight),
                initialArgb = theme.highlightArgb,
                onDismiss = { colorTarget = null },
                onConfirm = { argb ->
                    theme = theme.copy(highlightArgb = argb)
                    colorTarget = null
                },
            )
        null -> Unit
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_book_theme_editor_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(scroll),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = theme.fontFamilyName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.settings_epub_font_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = fontNames.isNotEmpty()) { expanded = true },
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                    ) {
                        LazyColumn {
                            items(fontNames) { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        expanded = false
                                        theme = theme.copy(fontFamilyName = name)
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(
                        R.string.settings_book_theme_font_size_pct,
                        (theme.fontSizePercent * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Slider(
                    value = theme.fontSizePercent.toFloat(),
                    onValueChange = { v ->
                        theme = theme.copy(fontSizePercent = v.toDouble().coerceIn(0.1, 5.0))
                    },
                    valueRange = 0.5f..2f,
                    modifier = Modifier.fillMaxWidth(),
                )
                ThemeColorSwatch(
                    colorArgb = theme.textArgb,
                    label = stringResource(R.string.reader_color_text),
                    onClick = { colorTarget = GlobalEditorColorTarget.Text },
                )
                ThemeColorSwatch(
                    colorArgb = theme.pageArgb,
                    label = stringResource(R.string.reader_color_page),
                    onClick = { colorTarget = GlobalEditorColorTarget.Page },
                )
                ThemeColorSwatch(
                    colorArgb = theme.highlightArgb,
                    label = stringResource(R.string.reader_color_highlight),
                    onClick = { colorTarget = GlobalEditorColorTarget.Highlight },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                ReaderGlobalThemePreferences(readerOptionsPrefs = readerOptionsPrefs)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(theme)
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
