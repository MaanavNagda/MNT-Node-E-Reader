package com.example.optireader.ui

import android.content.Context
import android.content.SharedPreferences

const val KEY_EPUB_GLOBAL_PARAGRAPH_SPACING_PERCENT = "epub_global_paragraph_spacing_percent"
const val KEY_EPUB_GLOBAL_LINE_SPACING_STEP = "epub_global_line_spacing_step"

const val DEFAULT_PARAGRAPH_SPACING_PERCENT = 70
const val DEFAULT_LINE_SPACING_STEP = 3

fun loadParagraphSpacingPercent(prefs: SharedPreferences): Int =
    prefs.getInt(KEY_EPUB_GLOBAL_PARAGRAPH_SPACING_PERCENT, DEFAULT_PARAGRAPH_SPACING_PERCENT)
        .coerceIn(0, 200)

fun loadLineSpacingStep(prefs: SharedPreferences): Int =
    prefs.getInt(KEY_EPUB_GLOBAL_LINE_SPACING_STEP, DEFAULT_LINE_SPACING_STEP)
        .coerceIn(-5, 20)

fun loadParagraphSpacingPercent(context: Context): Int =
    loadParagraphSpacingPercent(
        context.getSharedPreferences("reader_options", Context.MODE_PRIVATE),
    )

fun loadLineSpacingStep(context: Context): Int =
    loadLineSpacingStep(
        context.getSharedPreferences("reader_options", Context.MODE_PRIVATE),
    )

/**
 * Maps UI line spacing step (-5..20) to Readium [EpubPreferences.lineHeight].
 * Step 3 is anchored to Readium's typical default (1.2); -5 → 1.0, 20 → 2.0.
 */
fun lineSpacingStepToReadiumLineHeight(step: Int): Double {
    val u = step.coerceIn(-5, 20)
    return if (u <= 3) {
        1.0 + (u + 5) * (0.2 / 8.0)
    } else {
        1.2 + (u - 3) * (0.8 / 17.0)
    }
}

/** Readium [paragraphSpacing] uses 0.0..2.0 as 0–200% of user spacing. */
fun paragraphSpacingPercentToReadium(percent: Int): Double =
    percent.coerceIn(0, 200) / 100.0
