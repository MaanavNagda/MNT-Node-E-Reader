package com.example.optireader.ui

import android.content.SharedPreferences
import android.graphics.Color

/** Default colors / font for EPUB/PDF book content when a book has no per-book overrides. */
data class EpubThemeBaseline(
    val fontFamilyName: String,
    val fontSizePercent: Double,
    val textArgb: Int,
    val pageArgb: Int,
    val highlightArgb: Int,
)

const val KEY_GLOBAL_BOOK_THEME_MODE = "global_book_theme_mode"
const val KEY_GLOBAL_BOOK_THEME_PROFILE_NAME = "global_book_theme_profile_name"

object GlobalBookThemeModes {
    const val DEFAULT = "default"
    const val DAY = "day"
    const val NIGHT = "night"
    const val AMOLED = "amoled"
    const val NAMED = "named"
}

private val defaultHighlightArgb = 0xFFFFF59D.toInt()

/** Matches [org.readium.r2.navigator.preferences.FontFamily.SANS_SERIF] name used in EPUB prefs. */
private const val DEFAULT_EPUB_FONT_FAMILY = "sans-serif"

object ReaderBookThemePresets {
    /** Matches [OptireaderApp] day EPUB shell colors. */
    val dayStyle = EpubPageStyle(backgroundCss = "#AFF8FF", foregroundCss = "#000000")
    val nightStyle = EpubPageStyle(backgroundCss = "#23001D", foregroundCss = "#FFFFFF")
    val amoledStyle = EpubPageStyle(backgroundCss = "#000000", foregroundCss = "#FFFFFF")
}

private fun cssColorToArgb(css: String, fallback: Int): Int =
    runCatching { Color.parseColor(css.trim()) }.getOrElse { fallback }

fun readGlobalBookThemeModeString(prefs: SharedPreferences): String =
    prefs.getString(KEY_GLOBAL_BOOK_THEME_MODE, GlobalBookThemeModes.DEFAULT) ?: GlobalBookThemeModes.DEFAULT

fun readGlobalBookThemeProfileName(prefs: SharedPreferences): String? =
    prefs.getString(KEY_GLOBAL_BOOK_THEME_PROFILE_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }

fun writeGlobalBookThemeSelection(
    prefs: SharedPreferences,
    mode: String,
    namedProfile: String? = null,
) {
    val e = prefs.edit().putString(KEY_GLOBAL_BOOK_THEME_MODE, mode)
    if (mode == GlobalBookThemeModes.NAMED && !namedProfile.isNullOrBlank()) {
        e.putString(KEY_GLOBAL_BOOK_THEME_PROFILE_NAME, namedProfile.trim())
    } else {
        e.remove(KEY_GLOBAL_BOOK_THEME_PROFILE_NAME)
    }
    e.apply()
}

fun baselineFromEpubPageStyle(style: EpubPageStyle): EpubThemeBaseline {
    val text = cssColorToArgb(style.foregroundCss, 0xFF000000.toInt())
    val page = cssColorToArgb(style.backgroundCss, 0xFFAFF8FF.toInt())
    return EpubThemeBaseline(
        fontFamilyName = DEFAULT_EPUB_FONT_FAMILY,
        fontSizePercent = 1.0,
        textArgb = text,
        pageArgb = page,
        highlightArgb = defaultHighlightArgb,
    )
}

/**
 * Baseline EPUB/PDF appearance for books with no per-book theme keys.
 * Separate from app chrome; [appShellStyle] is used only when mode is [MODE_DEFAULT].
 */
fun resolveEpubThemeBaseline(
    prefs: SharedPreferences,
    appShellStyle: EpubPageStyle,
): EpubThemeBaseline {
    return when (val mode = readGlobalBookThemeModeString(prefs)) {
        GlobalBookThemeModes.DEFAULT -> baselineFromEpubPageStyle(appShellStyle)
        GlobalBookThemeModes.DAY -> baselineFromEpubPageStyle(ReaderBookThemePresets.dayStyle)
        GlobalBookThemeModes.NIGHT -> baselineFromEpubPageStyle(ReaderBookThemePresets.nightStyle)
        GlobalBookThemeModes.AMOLED -> baselineFromEpubPageStyle(ReaderBookThemePresets.amoledStyle)
        GlobalBookThemeModes.NAMED -> {
            val name = readGlobalBookThemeProfileName(prefs) ?: return baselineFromEpubPageStyle(appShellStyle)
            val profile = loadReaderThemeProfiles(prefs)
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
            val t = profile?.epubTextArgb
            val p = profile?.epubPageArgb
            val h = profile?.epubHighlightArgb
            if (profile != null && t != null && p != null && h != null) {
                EpubThemeBaseline(
                    fontFamilyName = profile.epubFontFamily ?: DEFAULT_EPUB_FONT_FAMILY,
                    fontSizePercent = (profile.epubFontSizePercent ?: 1f).toDouble().coerceIn(0.1, 5.0),
                    textArgb = t,
                    pageArgb = p,
                    highlightArgb = h,
                )
            } else {
                baselineFromEpubPageStyle(ReaderBookThemePresets.dayStyle)
            }
        }
        else -> baselineFromEpubPageStyle(appShellStyle)
    }
}
