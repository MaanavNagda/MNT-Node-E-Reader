package com.example.optireader.ui

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

private const val KEY_PROFILES_JSON = "reader_theme_profiles_json"

/**
 * Saved reader profile: global EPUB layout, typography, paragraph/line spacing, margins, and page turn.
 * Stored as a JSON array in [reader_options] under [KEY_PROFILES_JSON].
 */
data class ReaderThemeProfile(
    val name: String,
    val globalPageViewDefault: Boolean,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val shadow: Boolean,
    val paragraphSpacingPercent: Int,
    val lineSpacingStep: Int,
    val marginLeft: Int = DEFAULT_EPUB_GLOBAL_MARGIN_LEFT,
    val marginRight: Int = DEFAULT_EPUB_GLOBAL_MARGIN_RIGHT,
    val marginTop: Int = DEFAULT_EPUB_GLOBAL_MARGIN_TOP,
    val marginBottom: Int = DEFAULT_EPUB_GLOBAL_MARGIN_BOTTOM,
    val pageTurnMode: Int = DEFAULT_READER_PAGE_TURN_MODE,
    val pageTurnSpeed: Int = DEFAULT_READER_PAGE_TURN_SPEED,
    /** Optional EPUB/PDF book-content palette (global default when this profile is selected). */
    val epubFontFamily: String? = null,
    val epubFontSizePercent: Float? = null,
    val epubTextArgb: Int? = null,
    val epubPageArgb: Int? = null,
    val epubHighlightArgb: Int? = null,
) {
    fun applyToPrefs(prefs: SharedPreferences) {
        prefs.edit()
            .putBoolean("global_page_view_default", globalPageViewDefault)
            .putBoolean("epub_global_text_bold", bold)
            .putBoolean("epub_global_text_italic", italic)
            .putBoolean("epub_global_text_underline", underline)
            .putBoolean("epub_global_text_shadow", shadow)
            .putInt(KEY_EPUB_GLOBAL_PARAGRAPH_SPACING_PERCENT, paragraphSpacingPercent.coerceIn(0, 200))
            .putInt(KEY_EPUB_GLOBAL_LINE_SPACING_STEP, lineSpacingStep.coerceIn(-5, 20))
            .putInt(KEY_READER_PAGE_TURN_MODE, pageTurnMode.coerceIn(0, 4))
            .putInt(KEY_READER_PAGE_TURN_SPEED, pageTurnSpeed.coerceIn(0, 100))
            .apply()
    }

    companion object {
        fun fromPrefs(prefs: SharedPreferences): ReaderThemeProfile {
            val m = loadEpubGlobalMargins(prefs)
            return ReaderThemeProfile(
                name = "",
                globalPageViewDefault = prefs.getBoolean("global_page_view_default", false),
                bold = prefs.getBoolean("epub_global_text_bold", false),
                italic = prefs.getBoolean("epub_global_text_italic", false),
                underline = prefs.getBoolean("epub_global_text_underline", false),
                shadow = prefs.getBoolean("epub_global_text_shadow", false),
                paragraphSpacingPercent = loadParagraphSpacingPercent(prefs),
                lineSpacingStep = loadLineSpacingStep(prefs),
                marginLeft = m.left,
                marginRight = m.right,
                marginTop = m.top,
                marginBottom = m.bottom,
                pageTurnMode = prefs.getInt(KEY_READER_PAGE_TURN_MODE, DEFAULT_READER_PAGE_TURN_MODE).coerceIn(0, 4),
                pageTurnSpeed = prefs.getInt(KEY_READER_PAGE_TURN_SPEED, DEFAULT_READER_PAGE_TURN_SPEED).coerceIn(0, 100),
                epubFontFamily = null,
                epubFontSizePercent = null,
                epubTextArgb = null,
                epubPageArgb = null,
                epubHighlightArgb = null,
            )
        }

        fun fromJson(o: JSONObject): ReaderThemeProfile? =
            runCatching {
                ReaderThemeProfile(
                    name = o.getString("name"),
                    globalPageViewDefault = o.getBoolean("globalPageViewDefault"),
                    bold = o.getBoolean("bold"),
                    italic = o.getBoolean("italic"),
                    underline = o.getBoolean("underline"),
                    shadow = o.getBoolean("shadow"),
                    paragraphSpacingPercent = o.getInt("paragraphSpacingPercent"),
                    lineSpacingStep = o.getInt("lineSpacingStep"),
                    marginLeft = o.getInt("marginLeft"),
                    marginRight = o.getInt("marginRight"),
                    marginTop = o.getInt("marginTop"),
                    marginBottom = o.getInt("marginBottom"),
                    pageTurnMode = o.optInt("pageTurnMode", DEFAULT_READER_PAGE_TURN_MODE).coerceIn(0, 4),
                    pageTurnSpeed = o.optInt("pageTurnSpeed", DEFAULT_READER_PAGE_TURN_SPEED).coerceIn(0, 100),
                    epubFontFamily = if (o.has("epubFontFamily")) o.optString("epubFontFamily").takeIf { it.isNotBlank() } else null,
                    epubFontSizePercent = if (o.has("epubFontSizePercent")) o.getDouble("epubFontSizePercent").toFloat() else null,
                    epubTextArgb = if (o.has("epubTextArgb")) o.optInt("epubTextArgb") else null,
                    epubPageArgb = if (o.has("epubPageArgb")) o.optInt("epubPageArgb") else null,
                    epubHighlightArgb = if (o.has("epubHighlightArgb")) o.optInt("epubHighlightArgb") else null,
                )
            }.getOrNull()
    }

    fun toJson(): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("globalPageViewDefault", globalPageViewDefault)
            put("bold", bold)
            put("italic", italic)
            put("underline", underline)
            put("shadow", shadow)
            put("paragraphSpacingPercent", paragraphSpacingPercent)
            put("lineSpacingStep", lineSpacingStep)
            put("marginLeft", marginLeft)
            put("marginRight", marginRight)
            put("marginTop", marginTop)
            put("marginBottom", marginBottom)
            put("pageTurnMode", pageTurnMode)
            put("pageTurnSpeed", pageTurnSpeed)
            epubFontFamily?.let { put("epubFontFamily", it) }
            epubFontSizePercent?.let { put("epubFontSizePercent", it.toDouble()) }
            epubTextArgb?.let { put("epubTextArgb", it) }
            epubPageArgb?.let { put("epubPageArgb", it) }
            epubHighlightArgb?.let { put("epubHighlightArgb", it) }
        }

    fun withEpubFromBaseline(b: EpubThemeBaseline): ReaderThemeProfile =
        copy(
            epubFontFamily = b.fontFamilyName,
            epubFontSizePercent = b.fontSizePercent.toFloat().coerceIn(0.1f, 5f),
            epubTextArgb = b.textArgb,
            epubPageArgb = b.pageArgb,
            epubHighlightArgb = b.highlightArgb,
        )
}

fun loadReaderThemeProfiles(prefs: SharedPreferences): List<ReaderThemeProfile> {
    val raw = prefs.getString(KEY_PROFILES_JSON, null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val p = ReaderThemeProfile.fromJson(o) ?: continue
                if (p.name.isNotBlank()) add(p)
            }
        }
    }.getOrElse { emptyList() }
}

/**
 * Inserts or replaces a profile with the same name (case-insensitive match on trimmed name).
 */
fun upsertReaderThemeProfile(prefs: SharedPreferences, profile: ReaderThemeProfile) {
    val key = profile.name.trim()
    if (key.isEmpty()) return
    val existing = loadReaderThemeProfiles(prefs).filterNot {
        it.name.trim().equals(key, ignoreCase = true)
    }
    val merged = existing + profile.copy(name = key)
    val arr = JSONArray()
    merged.sortedBy { it.name.lowercase() }.forEach { arr.put(it.toJson()) }
    prefs.edit().putString(KEY_PROFILES_JSON, arr.toString()).apply()
}
