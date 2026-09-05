package com.example.mntnode.ui

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/** Global EPUB typography (Reader settings, not per-book). Stored in `reader_options`. */
data class ReaderGlobalEpubTypography(
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val shadow: Boolean,
) {
    val needsCssInjection: Boolean get() = italic || underline || shadow
}

private const val KEY_BOLD = "epub_global_text_bold"
private const val KEY_ITALIC = "epub_global_text_italic"
private const val KEY_UNDERLINE = "epub_global_text_underline"
private const val KEY_SHADOW = "epub_global_text_shadow"

fun loadReaderGlobalEpubTypography(prefs: SharedPreferences): ReaderGlobalEpubTypography =
    ReaderGlobalEpubTypography(
        bold = prefs.getBoolean(KEY_BOLD, false),
        italic = prefs.getBoolean(KEY_ITALIC, false),
        underline = prefs.getBoolean(KEY_UNDERLINE, false),
        shadow = prefs.getBoolean(KEY_SHADOW, false),
)

fun loadReaderGlobalEpubTypography(context: Context): ReaderGlobalEpubTypography =
    loadReaderGlobalEpubTypography(
        context.getSharedPreferences("reader_options", Context.MODE_PRIVATE),
    )

internal fun buildGlobalEpubTypographyCss(t: ReaderGlobalEpubTypography): String {
    if (!t.needsCssInjection) return ""
    val fontStyle = if (t.italic) "italic" else "normal"
    val decoration = if (t.underline) "underline" else "none"
    val shadow = if (t.shadow) "0.06em 0.06em 0.14em rgba(0,0,0,0.45)" else "none"
    return """
        html, body, p, li, div, span, article, section, aside, header, footer,
        h1, h2, h3, h4, h5, h6, blockquote, figcaption, dd, dt, pre, code, td, th, em, strong, a {
            font-style: $fontStyle !important;
            text-decoration: $decoration !important;
            text-shadow: $shadow !important;
        }
    """.trimIndent()
}

/** Injects or removes Readium-wide typography CSS (italic / underline / shadow). */
internal suspend fun EpubNavigatorFragment.injectGlobalEpubTypographyCss(css: String) {
    val quoted = JSONObject.quote(css)
    val script = """
        (function() {
            var id = 'mntnode-user-typography';
            var el = document.getElementById(id);
            if (el) el.remove();
            var css = $quoted;
            if (!css) return;
            var s = document.createElement('style');
            s.id = id;
            s.type = 'text/css';
            s.appendChild(document.createTextNode(css));
            (document.head || document.documentElement).appendChild(s);
        })();
    """.trimIndent()
    evaluateJavascript(script)
}
