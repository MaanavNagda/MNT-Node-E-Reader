package com.example.optireader.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.optireader.R
import com.example.optireader.data.preferences.AppPreferences
import com.example.optireader.data.preferences.PreferencesRepository
import com.example.optireader.data.preferences.RecommendationAnchor
import kotlinx.coroutines.launch

@Composable
private fun BookThemeCurrentSummary(readerPrefs: SharedPreferences): String {
    val mode = readGlobalBookThemeModeString(readerPrefs)
    val name = readGlobalBookThemeProfileName(readerPrefs)
    return when (mode) {
        GlobalBookThemeModes.DEFAULT -> stringResource(R.string.settings_book_theme_opt_default)
        GlobalBookThemeModes.DAY -> stringResource(R.string.settings_book_theme_opt_day)
        GlobalBookThemeModes.NIGHT -> stringResource(R.string.settings_book_theme_opt_night)
        GlobalBookThemeModes.AMOLED -> stringResource(R.string.settings_book_theme_opt_amoled)
        GlobalBookThemeModes.NAMED -> name?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.settings_book_theme_opt_default)
        else -> stringResource(R.string.settings_book_theme_opt_default)
    }
}

@Composable
fun AppDrawerContent(
    prefs: AppPreferences,
    preferencesRepository: PreferencesRepository,
    epubPageStyle: EpubPageStyle,
    onExportStatistics: () -> Unit = {},
    showGlobalSettingsDialog: Boolean = false,
    onShowGlobalSettingsDialogChange: (Boolean) -> Unit = {},
    onGlobalSettingsConfirmed: () -> Unit = {},
    onGlobalSettingsDismissed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val readerOptionsPrefs = remember {
        context.getSharedPreferences("reader_options", Context.MODE_PRIVATE)
    }
    var showAbout by remember { mutableStateOf(false) }
    var globalEpubPageViewDefault by remember {
        mutableStateOf(readerOptionsPrefs.getBoolean("global_page_view_default", false))
    }
    var showLoadBookThemeDialog by remember { mutableStateOf(false) }
    var showNewBookThemeEditor by remember { mutableStateOf(false) }
    var showNameNewBookThemeDialog by remember { mutableStateOf(false) }
    var pendingBookThemeBaseline by remember { mutableStateOf<EpubThemeBaseline?>(null) }
    var newBookThemeName by remember { mutableStateOf("") }
    val loadBookThemeScroll = rememberScrollState()
    var drawerBookmarkColorArgb by remember { mutableIntStateOf(loadBookmarkIconColorArgb(readerOptionsPrefs)) }
    var showDrawerBookmarkColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(showGlobalSettingsDialog) {
        if (showGlobalSettingsDialog) {
            drawerBookmarkColorArgb = loadBookmarkIconColorArgb(readerOptionsPrefs)
        }
    }

    val systemDark = isSystemInDarkTheme()
    val railShowsDay = if (prefs.followSystemDarkMode) !systemDark else prefs.useDayTheme
    val amoledSwitchEnabled = !(prefs.followSystemDarkMode && !systemDark)

    if (showAbout) {
        val uriHandler = LocalUriHandler.current
        val linkedinUrl = stringResource(R.string.about_linkedin_url)
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.about_created_by),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.about_linkedin_prefix),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = linkedinUrl,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                            ),
                            modifier = Modifier.clickable {
                                uriHandler.openUri(linkedinUrl)
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.about_message_line),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_version_line),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    if (showGlobalSettingsDialog) {
        var globalControls by remember {
            mutableStateOf(loadGlobalReaderControlsSettings(readerOptionsPrefs))
        }
        AlertDialog(
            onDismissRequest = {
                onGlobalSettingsDismissed()
                onShowGlobalSettingsDialogChange(false)
            },
            title = { Text(stringResource(R.string.drawer_epub_default_layout)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 720.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    DrawerSwitchRow(
                        label = stringResource(R.string.drawer_hidden_files),
                        checked = prefs.listHiddenFiles,
                        onCheckedChange = { checked ->
                            scope.launch { preferencesRepository.setListHiddenFiles(checked) }
                        },
                    )
                    DrawerSwitchRow(
                        label = stringResource(R.string.drawer_amoled),
                        checked = prefs.useAmoledDark,
                        enabled = amoledSwitchEnabled,
                        onCheckedChange = { checked ->
                            scope.launch { preferencesRepository.setUseAmoledDark(checked) }
                        },
                    )
                    DrawerSwitchRow(
                        label = stringResource(R.string.drawer_follow_system),
                        checked = prefs.followSystemDarkMode,
                        onCheckedChange = { checked ->
                            scope.launch { preferencesRepository.setFollowSystemDarkMode(checked) }
                        },
                    )
                    DrawerSwitchRow(
                        label = stringResource(R.string.drawer_reading_progress),
                        checked = prefs.showReadingProgress,
                        onCheckedChange = { checked ->
                            scope.launch { preferencesRepository.setShowReadingProgress(checked) }
                        },
                    )
                    DrawerSwitchRow(
                        label = stringResource(R.string.drawer_reader_power_closes_app),
                        checked = prefs.readerPowerButtonClosesApp,
                        onCheckedChange = { checked ->
                            scope.launch { preferencesRepository.setReaderPowerButtonClosesApp(checked) }
                        },
                    )
                    DrawerSwitchRow(
                        label = stringResource(R.string.drawer_recommendations_enabled),
                        checked = prefs.recommendationsEnabled,
                        onCheckedChange = { checked ->
                            scope.launch { preferencesRepository.setRecommendationsEnabled(checked) }
                        },
                    )
                    if (prefs.recommendationsEnabled) {
                        Text(
                            text = stringResource(R.string.drawer_recommendation_anchor_label),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        RecommendationAnchor.entries.forEach { anchor ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { preferencesRepository.setRecommendationAnchor(anchor) }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = prefs.recommendationAnchor == anchor,
                                    onClick = {
                                        scope.launch { preferencesRepository.setRecommendationAnchor(anchor) }
                                    },
                                )
                                Text(
                                    text = stringResource(recommendationAnchorLabelRes(anchor)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.settings_epub_default_view),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    EpubReaderLayoutModeToggle(
                        pageView = globalEpubPageViewDefault,
                        onPageViewChange = { page ->
                            globalEpubPageViewDefault = page
                            readerOptionsPrefs.edit()
                                .putBoolean("global_page_view_default", page)
                                .apply()
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_epub_default_view_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    ReaderControlsSettingsSection(
                        settings = globalControls,
                        onSettingsChange = { next ->
                            globalControls = next
                            saveGlobalReaderControlsSettings(readerOptionsPrefs, next)
                        },
                    )
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
                        colorArgb = drawerBookmarkColorArgb,
                        label = stringResource(R.string.settings_bookmark_icon_color),
                        onClick = { showDrawerBookmarkColorPicker = true },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.settings_book_theme_section_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.settings_book_theme_section_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    Text(
                        text = buildString {
                            append(stringResource(R.string.settings_book_theme_current))
                            append(": ")
                            append(BookThemeCurrentSummary(readerOptionsPrefs))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = { showLoadBookThemeDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.settings_book_theme_load))
                        }
                        TextButton(
                            onClick = {
                                pendingBookThemeBaseline = null
                                showNewBookThemeEditor = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.settings_book_theme_new))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onGlobalSettingsConfirmed()
                        onShowGlobalSettingsDialogChange(false)
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    if (showDrawerBookmarkColorPicker) {
        HsvColorPickerDialog(
            title = stringResource(R.string.settings_bookmark_icon_color),
            initialArgb = drawerBookmarkColorArgb,
            onDismiss = { showDrawerBookmarkColorPicker = false },
            onConfirm = { argb ->
                drawerBookmarkColorArgb = argb
                saveBookmarkIconColorArgb(readerOptionsPrefs, argb)
                showDrawerBookmarkColorPicker = false
            },
        )
    }

    if (showLoadBookThemeDialog) {
        val savedProfiles = loadReaderThemeProfiles(readerOptionsPrefs)
        AlertDialog(
            onDismissRequest = { showLoadBookThemeDialog = false },
            title = { Text(stringResource(R.string.settings_book_theme_load_dialog_title)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(loadBookThemeScroll),
                ) {
                    TextButton(
                        onClick = {
                            writeGlobalBookThemeSelection(readerOptionsPrefs, GlobalBookThemeModes.DEFAULT)
                            showLoadBookThemeDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_book_theme_opt_default))
                    }
                    TextButton(
                        onClick = {
                            writeGlobalBookThemeSelection(readerOptionsPrefs, GlobalBookThemeModes.DAY)
                            showLoadBookThemeDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_book_theme_opt_day))
                    }
                    TextButton(
                        onClick = {
                            writeGlobalBookThemeSelection(readerOptionsPrefs, GlobalBookThemeModes.NIGHT)
                            showLoadBookThemeDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_book_theme_opt_night))
                    }
                    TextButton(
                        onClick = {
                            writeGlobalBookThemeSelection(readerOptionsPrefs, GlobalBookThemeModes.AMOLED)
                            showLoadBookThemeDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_book_theme_opt_amoled))
                    }
                    if (savedProfiles.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        savedProfiles.forEach { profile ->
                            TextButton(
                                onClick = {
                                    profile.applyToPrefs(readerOptionsPrefs)
                                    writeGlobalBookThemeSelection(
                                        readerOptionsPrefs,
                                        GlobalBookThemeModes.NAMED,
                                        profile.name,
                                    )
                                    showLoadBookThemeDialog = false
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
                TextButton(onClick = { showLoadBookThemeDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    if (showNewBookThemeEditor) {
        GlobalReaderBookThemeEditorDialog(
            initialBaseline = resolveEpubThemeBaseline(readerOptionsPrefs, epubPageStyle),
            readerOptionsPrefs = readerOptionsPrefs,
            onDismiss = { showNewBookThemeEditor = false },
            onConfirm = { baseline ->
                pendingBookThemeBaseline = baseline
                showNewBookThemeEditor = false
                newBookThemeName = ""
                showNameNewBookThemeDialog = true
            },
        )
    }

    if (showNameNewBookThemeDialog) {
        AlertDialog(
            onDismissRequest = {
                showNameNewBookThemeDialog = false
                pendingBookThemeBaseline = null
            },
            title = { Text(stringResource(R.string.settings_book_theme_name_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_book_theme_name_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = newBookThemeName,
                        onValueChange = { newBookThemeName = it },
                        label = { Text(stringResource(R.string.settings_reader_theme_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                val canSave = newBookThemeName.trim().isNotEmpty()
                TextButton(
                    enabled = canSave,
                    onClick = {
                        val trimmed = newBookThemeName.trim()
                        val b = pendingBookThemeBaseline
                        if (trimmed.isNotEmpty() && b != null) {
                            val base = ReaderThemeProfile.fromPrefs(readerOptionsPrefs)
                            val named = base.copy(name = trimmed).withEpubFromBaseline(b)
                            upsertReaderThemeProfile(readerOptionsPrefs, named)
                            writeGlobalBookThemeSelection(
                                readerOptionsPrefs,
                                GlobalBookThemeModes.NAMED,
                                trimmed,
                            )
                        }
                        showNameNewBookThemeDialog = false
                        pendingBookThemeBaseline = null
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNameNewBookThemeDialog = false
                        pendingBookThemeBaseline = null
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            DayNightThemeRail(
                useDayTheme = railShowsDay,
                useAmoledDark = prefs.useAmoledDark,
                enabled = !prefs.followSystemDarkMode,
                onToggle = {
                    scope.launch {
                        preferencesRepository.setUseDayTheme(!prefs.useDayTheme)
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            DrawerIconNavRow(
                label = stringResource(R.string.drawer_epub_default_layout),
                onClick = {
                    globalEpubPageViewDefault =
                        readerOptionsPrefs.getBoolean("global_page_view_default", false)
                    onShowGlobalSettingsDialogChange(true)
                },
            )
            DrawerNavRow(
                label = stringResource(R.string.drawer_about),
                onClick = { showAbout = true },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onExportStatistics,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.drawer_export_statistics))
            }
        }
    }
}

@Composable
private fun recommendationAnchorLabelRes(a: RecommendationAnchor): Int =
    when (a) {
        RecommendationAnchor.SameAuthor -> R.string.recommendation_anchor_same_author
        RecommendationAnchor.SameGenre -> R.string.recommendation_anchor_same_genre
        RecommendationAnchor.DifferentAuthor -> R.string.recommendation_anchor_different_author
        RecommendationAnchor.DifferentGenre -> R.string.recommendation_anchor_different_genre
    }

@Composable
private fun DrawerSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
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
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun DrawerNavRow(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DrawerIconNavRow(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}
