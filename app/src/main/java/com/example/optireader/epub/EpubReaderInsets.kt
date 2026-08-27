package com.example.optireader.epub

import android.content.Context
import android.util.TypedValue
import kotlin.math.roundToInt

/**
 * Reading frame: ~1 cm from top, ~0.7 cm from left/right/bottom (physical mm, scales per device).
 *
 * [border*LayoutPx] adds a few device pixels on top of the drawn border bands so the WebView clip
 * rect never encroaches past the visible inner edge (Compose Dp↔px and WebView CSS px rounding).
 */
object EpubReaderInsets {
    private const val TOP_MM = 10f
    private const val SIDE_MM = 7f
    private const val BOTTOM_MM = 7f

    /** Visual frame on the EPUB reader: top/bottom band thickness (0.8 cm). */
    private const val BORDER_TOP_BOTTOM_MM = 8f

    /** Visual frame on the EPUB reader: left/right band thickness (0.6 cm). */
    private const val BORDER_SIDE_MM = 6f

    /**
     * Outline stroke for [com.example.optireader.ui.ReaderScreens.EpubReaderBorderOverlay] (2 dp).
     * Stroke is centered on the band/content boundary, so [borderStrokeHalfPx] is the inset from the
     * band rect to the inner edge of the line — WebView padding should be band px + half stroke.
     */
    private const val BORDER_STROKE_DP = 2f

    fun borderStrokeWidthPx(context: Context): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            BORDER_STROKE_DP,
            context.resources.displayMetrics,
        ).roundToInt().coerceAtLeast(1)

    fun borderStrokeHalfPx(context: Context): Int = (borderStrokeWidthPx(context) + 1) / 2

    fun topPx(context: Context): Int = mmToPx(context, TOP_MM)
    fun sidePx(context: Context): Int = mmToPx(context, SIDE_MM)
    fun bottomPx(context: Context): Int = mmToPx(context, BOTTOM_MM)

    fun borderTopBottomPx(context: Context): Int = mmToPx(context, BORDER_TOP_BOTTOM_MM)

    fun borderSidePx(context: Context): Int = mmToPx(context, BORDER_SIDE_MM)

    /** Physical px inset for WebView layout = border band + slack (still inside full screen). */
    fun borderTopBottomLayoutPx(context: Context): Int = borderTopBottomPx(context) + borderWebViewSlackPx(context)

    fun borderSideLayoutPx(context: Context): Int = borderSidePx(context) + borderWebViewSlackPx(context)

    /**
     * ~0.5–1 mm equivalent on typical phones (2–4 px), capped so low-density devices still get slack.
     */
    private fun borderWebViewSlackPx(context: Context): Int {
        val d = context.resources.displayMetrics.density
        return (d * 2f).roundToInt().coerceIn(2, 5)
    }

    fun mmToPx(context: Context, mm: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM,
            mm,
            context.resources.displayMetrics,
        ).roundToInt().coerceAtLeast(0)
    }
}
