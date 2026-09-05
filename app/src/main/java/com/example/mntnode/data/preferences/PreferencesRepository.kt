package com.example.mntnode.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mntnode_prefs")

data class AppPreferences(
    val followSystemDarkMode: Boolean = true,
    /** When follow system theme is off: true = light (day), false = dark. */
    val useDayTheme: Boolean = false,
    val useAmoledDark: Boolean = false,
    val listHiddenFiles: Boolean = false,
    val showReadingProgress: Boolean = true,
    /** Reader power icon behavior: false = go home, true = close app. */
    val readerPowerButtonClosesApp: Boolean = false,
    /** Suggest TBR books after finishing a read. */
    val recommendationsEnabled: Boolean = false,
    /** How the book you just finished steers the next pick. */
    val recommendationAnchor: RecommendationAnchor = RecommendationAnchor.SameAuthor,
    /** After welcome: user should land on Import until the first-import wizard runs. */
    val pendingFirstImportOnboarding: Boolean = false,
    /** After first batch import wizard completes; normal import thereafter. */
    val firstImportWizardCompleted: Boolean = false,
)

class PreferencesRepository(private val context: Context) {

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { p ->
        AppPreferences(
            followSystemDarkMode = p[KEY_FOLLOW_SYSTEM] ?: true,
            useDayTheme = p[KEY_DAY_THEME] ?: false,
            useAmoledDark = p[KEY_AMOLED] ?: false,
            listHiddenFiles = p[KEY_HIDDEN] ?: false,
            showReadingProgress = p[KEY_READING_PROGRESS] ?: true,
            readerPowerButtonClosesApp = p[KEY_READER_POWER_CLOSES_APP] ?: false,
            recommendationsEnabled = p[KEY_RECOMMENDATIONS_ENABLED] ?: false,
            recommendationAnchor = RecommendationAnchor.fromStorage(p[KEY_RECOMMENDATION_ANCHOR]),
            pendingFirstImportOnboarding = p[KEY_PENDING_FIRST_IMPORT_ONBOARDING] ?: false,
            firstImportWizardCompleted = p[KEY_FIRST_IMPORT_WIZARD_COMPLETED] ?: false,
        )
    }

    suspend fun setFollowSystemDarkMode(value: Boolean) {
        context.dataStore.edit { it[KEY_FOLLOW_SYSTEM] = value }
    }

    suspend fun setUseDayTheme(value: Boolean) {
        context.dataStore.edit { it[KEY_DAY_THEME] = value }
    }

    suspend fun setUseAmoledDark(value: Boolean) {
        context.dataStore.edit { p ->
            if (value) {
                val follow = p[KEY_FOLLOW_SYSTEM] ?: true
                val dayTheme = p[KEY_DAY_THEME] ?: false
                if (!follow && dayTheme) {
                    p[KEY_DAY_THEME] = false
                }
                p[KEY_AMOLED] = true
            } else {
                p[KEY_AMOLED] = false
            }
        }
    }

    suspend fun setListHiddenFiles(value: Boolean) {
        context.dataStore.edit { it[KEY_HIDDEN] = value }
    }

    suspend fun setShowReadingProgress(value: Boolean) {
        context.dataStore.edit { it[KEY_READING_PROGRESS] = value }
    }

    suspend fun setReaderPowerButtonClosesApp(value: Boolean) {
        context.dataStore.edit { it[KEY_READER_POWER_CLOSES_APP] = value }
    }

    suspend fun setRecommendationsEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_RECOMMENDATIONS_ENABLED] = value }
    }

    suspend fun setRecommendationAnchor(value: RecommendationAnchor) {
        context.dataStore.edit { it[KEY_RECOMMENDATION_ANCHOR] = RecommendationAnchor.toStorage(value) }
    }

    suspend fun setPendingFirstImportOnboarding(value: Boolean) {
        context.dataStore.edit { it[KEY_PENDING_FIRST_IMPORT_ONBOARDING] = value }
    }

    suspend fun setFirstImportWizardCompleted(value: Boolean) {
        context.dataStore.edit { it[KEY_FIRST_IMPORT_WIZARD_COMPLETED] = value }
    }

    companion object {
        private val KEY_FOLLOW_SYSTEM = booleanPreferencesKey("follow_system_dark")
        private val KEY_DAY_THEME = booleanPreferencesKey("day_theme")
        private val KEY_AMOLED = booleanPreferencesKey("amoled_dark")
        private val KEY_HIDDEN = booleanPreferencesKey("list_hidden")
        private val KEY_READING_PROGRESS = booleanPreferencesKey("reading_progress")
        private val KEY_READER_POWER_CLOSES_APP = booleanPreferencesKey("reader_power_closes_app")
        private val KEY_RECOMMENDATIONS_ENABLED = booleanPreferencesKey("recommendations_enabled")
        private val KEY_RECOMMENDATION_ANCHOR = stringPreferencesKey("recommendation_anchor")
        private val KEY_PENDING_FIRST_IMPORT_ONBOARDING = booleanPreferencesKey("pending_first_import_onboarding")
        private val KEY_FIRST_IMPORT_WIZARD_COMPLETED = booleanPreferencesKey("first_import_wizard_completed")
    }
}
