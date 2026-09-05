package com.example.mntnode.ui

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment

const val KEY_EPUB_GLOBAL_MARGIN_LEFT = "epub_global_margin_left"
const val KEY_EPUB_GLOBAL_MARGIN_RIGHT = "epub_global_margin_right"
const val KEY_EPUB_GLOBAL_MARGIN_TOP = "epub_global_margin_top"
const val KEY_EPUB_GLOBAL_MARGIN_BOTTOM = "epub_global_margin_bottom"

const val DEFAULT_EPUB_GLOBAL_MARGIN_LEFT = 25
const val DEFAULT_EPUB_GLOBAL_MARGIN_RIGHT = 25
const val DEFAULT_EPUB_GLOBAL_MARGIN_TOP = 50
const val DEFAULT_EPUB_GLOBAL_MARGIN_BOTTOM = 50

/**
 * Per-edge margin UI value (0–200). **Unitless scale** (not px):
 * - Left/right: **25** ≈ prior “default” horizontal inset; each slider only changes that side.
 * - Top/bottom: **50** ≈ prior default vertical inset; each slider only changes that edge.
 */
data class EpubGlobalMargins(
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int,
)

/**
 * Readium [EpubPreferences.pageMargins] is a single horizontal factor; we use **0** and apply
 * per-side padding via injected CSS so left/right are independent.
 */
internal fun readiumPageMarginsDisabledForCssMargins(): Double = 0.0

/** Left/right UI → vw per side (25 → ~4vw). */
private fun horizontalUiToVw(v: Int): Double {
    val u = v.coerceIn(0, 200)
    return (u / 25.0) * 4.0
}

/** Top/bottom UI → vh per edge (50 → ~4vh). */
private fun verticalUiToVh(v: Int): Double {
    val u = v.coerceIn(0, 200)
    return (u / 50.0) * 4.0
}

fun loadEpubGlobalMargins(prefs: SharedPreferences): EpubGlobalMargins =
    EpubGlobalMargins(
        left = prefs.getInt(KEY_EPUB_GLOBAL_MARGIN_LEFT, DEFAULT_EPUB_GLOBAL_MARGIN_LEFT).coerceIn(0, 200),
        right = prefs.getInt(KEY_EPUB_GLOBAL_MARGIN_RIGHT, DEFAULT_EPUB_GLOBAL_MARGIN_RIGHT).coerceIn(0, 200),
        top = prefs.getInt(KEY_EPUB_GLOBAL_MARGIN_TOP, DEFAULT_EPUB_GLOBAL_MARGIN_TOP).coerceIn(0, 200),
        bottom = prefs.getInt(KEY_EPUB_GLOBAL_MARGIN_BOTTOM, DEFAULT_EPUB_GLOBAL_MARGIN_BOTTOM).coerceIn(0, 200),
    )

fun loadEpubGlobalMargins(context: Context): EpubGlobalMargins =
    loadEpubGlobalMargins(context.getSharedPreferences("reader_options", Context.MODE_PRIVATE))

internal fun buildGlobalEpubMarginCss(m: EpubGlobalMargins): String {
    val leftVw = horizontalUiToVw(m.left)
    val rightVw = horizontalUiToVw(m.right)
    val topVh = verticalUiToVh(m.top)
    val bottomVh = verticalUiToVh(m.bottom)
    if (leftVw <= 0.0 && rightVw <= 0.0 && topVh <= 0.0 && bottomVh <= 0.0) return ""
    val ls = String.format(Locale.US, "%.4f", leftVw)
    val rs = String.format(Locale.US, "%.4f", rightVw)
    val ts = String.format(Locale.US, "%.4f", topVh)
    val bs = String.format(Locale.US, "%.4f", bottomVh)
    return """
        html {
            padding-left: ${ls}vw !important;
            padding-right: ${rs}vw !important;
            padding-top: ${ts}vh !important;
            padding-bottom: ${bs}vh !important;
            box-sizing: border-box !important;
        }
    """.trimIndent()
}

/** Injects per-edge margin CSS (Readium [pageMargins] is set to 0 so this is the sole source). */
internal suspend fun EpubNavigatorFragment.injectGlobalEpubMarginCss(css: String) {
    val quoted = JSONObject.quote(css)
    val script = """
        (function() {
            var id = 'mntnode-user-margins';
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
