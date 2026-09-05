package com.example.mntnode.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

const val KEY_READER_PAGE_TURN_MODE = "reader_page_turn_mode"
const val KEY_READER_PAGE_TURN_SPEED = "reader_page_turn_speed"

const val DEFAULT_READER_PAGE_TURN_MODE = 0
const val DEFAULT_READER_PAGE_TURN_SPEED = 50

enum class ReaderPageTurnMode(val value: Int) {
    NONE(0),
    FLIP(3),
    ;

    companion object {
        fun fromValue(v: Int): ReaderPageTurnMode = when (v) {
            0 -> NONE
            1 -> NONE // Legacy "Slide" now maps to None.
            2 -> NONE // Removed "Card" mode.
            3 -> FLIP
            4 -> FLIP // Legacy "Fold" now maps to Flip.
            else -> NONE
        }
    }
}

fun loadReaderPageTurnMode(prefs: SharedPreferences): ReaderPageTurnMode =
    ReaderPageTurnMode.fromValue(
        prefs.getInt(KEY_READER_PAGE_TURN_MODE, DEFAULT_READER_PAGE_TURN_MODE).coerceIn(0, 4),
    )

fun loadReaderPageTurnSpeed(prefs: SharedPreferences): Int =
    prefs.getInt(KEY_READER_PAGE_TURN_SPEED, DEFAULT_READER_PAGE_TURN_SPEED).coerceIn(0, 100)

data class ReaderPageTurnPrefs(
    val mode: ReaderPageTurnMode,
    val speed: Int,
)

fun loadReaderPageTurnPrefs(prefs: SharedPreferences): ReaderPageTurnPrefs =
    ReaderPageTurnPrefs(
        mode = loadReaderPageTurnMode(prefs),
        speed = loadReaderPageTurnSpeed(prefs),
    )

fun loadReaderPageTurnPrefs(context: Context): ReaderPageTurnPrefs =
    loadReaderPageTurnPrefs(context.getSharedPreferences("reader_options", Context.MODE_PRIVATE))

/** Keep Readium behavior unchanged regardless of selected visual mode. */
fun useReaderPageTurnAnimation(prefs: SharedPreferences): Boolean = true

/**
 * Horizontal pager snap animation for PDF. [speed] is 0 (slow) .. 100 (fast).
 */
fun pageTurnSnapAnimationSpec(mode: ReaderPageTurnMode, speed: Int): AnimationSpec<Float> {
    val s = speed.coerceIn(0, 100)
    val t = s / 100f
    val baseStiffness = 80f + t * (5200f - 80f)
    return when (mode) {
        ReaderPageTurnMode.NONE -> spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = baseStiffness,
        )
        ReaderPageTurnMode.FLIP -> tween(
            durationMillis = (460 - (s * 3.0)).toInt().coerceIn(140, 460),
        )
    }
}
