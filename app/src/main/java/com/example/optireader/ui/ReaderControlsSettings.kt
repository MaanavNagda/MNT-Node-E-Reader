package com.example.optireader.ui

import android.content.SharedPreferences

private const val KEY_PREFIX_BOOK = "book_reader_controls_"

const val KEY_RIGHT_SLIDE_VOLUME = "reader_right_slide_volume"
const val KEY_LEFT_SLIDE_BRIGHTNESS = "reader_left_slide_brightness"
const val KEY_VOLUME_KEYS_PAGE_TURN = "reader_volume_keys_page_turn"
const val KEY_VOLUME_UP_FORWARD = "reader_volume_up_forward"
const val KEY_VOLUME_KEY_ANIMATION_SPEED_MS = "reader_volume_key_animation_speed_ms"

const val READER_VOLUME_KEY_ANIMATION_SPEED_MIN_MS = 0
const val READER_VOLUME_KEY_ANIMATION_SPEED_MAX_MS = 600
const val READER_VOLUME_KEY_ANIMATION_SPEED_DEFAULT_MS = 220

const val KEY_STATUS_SHOW_BATTERY = "reader_status_show_battery"
const val KEY_STATUS_SHOW_CHAPTER_TIME = "reader_status_show_chapter_time"
const val KEY_STATUS_SHOW_TIME = "reader_status_show_time"
const val KEY_STATUS_SHOW_PAGE_COUNT = "reader_status_show_page_count"
const val KEY_STATUS_SHOW_PERCENTAGE = "reader_status_show_percentage"
const val KEY_STATUS_SHOW_BOOK_TIME = "reader_status_show_book_time"

data class ReaderControlsSettings(
    val rightSlideVolume: Boolean = false,
    val leftSlideBrightness: Boolean = false,
    val volumeKeysPageTurn: Boolean = false,
    /** True = volume up goes forward, volume down goes back (default). */
    val volumeUpForward: Boolean = true,
    val volumeKeyAnimationSpeedMs: Int = READER_VOLUME_KEY_ANIMATION_SPEED_DEFAULT_MS,
    val showBattery: Boolean = true,
    val showChapterTimeLeft: Boolean = true,
    val showTime: Boolean = true,
    val showPageCount: Boolean = true,
    val showPercentage: Boolean = true,
    val showBookTimeLeft: Boolean = true,
)

private fun bookKey(bookId: Long, key: String): String = "$KEY_PREFIX_BOOK${bookId}_$key"

fun loadGlobalReaderControlsSettings(prefs: SharedPreferences): ReaderControlsSettings =
    ReaderControlsSettings(
        rightSlideVolume = prefs.getBoolean(KEY_RIGHT_SLIDE_VOLUME, false),
        leftSlideBrightness = prefs.getBoolean(KEY_LEFT_SLIDE_BRIGHTNESS, false),
        volumeKeysPageTurn = prefs.getBoolean(KEY_VOLUME_KEYS_PAGE_TURN, false),
        volumeUpForward = prefs.getBoolean(KEY_VOLUME_UP_FORWARD, true),
        volumeKeyAnimationSpeedMs = prefs.getInt(
            KEY_VOLUME_KEY_ANIMATION_SPEED_MS,
            READER_VOLUME_KEY_ANIMATION_SPEED_DEFAULT_MS,
        ).coerceIn(READER_VOLUME_KEY_ANIMATION_SPEED_MIN_MS, READER_VOLUME_KEY_ANIMATION_SPEED_MAX_MS),
        showBattery = prefs.getBoolean(KEY_STATUS_SHOW_BATTERY, true),
        showChapterTimeLeft = prefs.getBoolean(KEY_STATUS_SHOW_CHAPTER_TIME, true),
        showTime = prefs.getBoolean(KEY_STATUS_SHOW_TIME, true),
        showPageCount = prefs.getBoolean(KEY_STATUS_SHOW_PAGE_COUNT, true),
        showPercentage = prefs.getBoolean(KEY_STATUS_SHOW_PERCENTAGE, true),
        showBookTimeLeft = prefs.getBoolean(KEY_STATUS_SHOW_BOOK_TIME, true),
    )

fun loadBookReaderControlsSettings(prefs: SharedPreferences, bookId: Long): ReaderControlsSettings {
    val g = loadGlobalReaderControlsSettings(prefs)
    return ReaderControlsSettings(
        rightSlideVolume = prefs.getBoolean(bookKey(bookId, KEY_RIGHT_SLIDE_VOLUME), g.rightSlideVolume),
        leftSlideBrightness = prefs.getBoolean(bookKey(bookId, KEY_LEFT_SLIDE_BRIGHTNESS), g.leftSlideBrightness),
        volumeKeysPageTurn = prefs.getBoolean(bookKey(bookId, KEY_VOLUME_KEYS_PAGE_TURN), g.volumeKeysPageTurn),
        volumeUpForward = prefs.getBoolean(bookKey(bookId, KEY_VOLUME_UP_FORWARD), g.volumeUpForward),
        volumeKeyAnimationSpeedMs = prefs.getInt(
            bookKey(bookId, KEY_VOLUME_KEY_ANIMATION_SPEED_MS),
            g.volumeKeyAnimationSpeedMs,
        ).coerceIn(READER_VOLUME_KEY_ANIMATION_SPEED_MIN_MS, READER_VOLUME_KEY_ANIMATION_SPEED_MAX_MS),
        showBattery = prefs.getBoolean(bookKey(bookId, KEY_STATUS_SHOW_BATTERY), g.showBattery),
        showChapterTimeLeft = prefs.getBoolean(bookKey(bookId, KEY_STATUS_SHOW_CHAPTER_TIME), g.showChapterTimeLeft),
        showTime = prefs.getBoolean(bookKey(bookId, KEY_STATUS_SHOW_TIME), g.showTime),
        showPageCount = prefs.getBoolean(bookKey(bookId, KEY_STATUS_SHOW_PAGE_COUNT), g.showPageCount),
        showPercentage = prefs.getBoolean(bookKey(bookId, KEY_STATUS_SHOW_PERCENTAGE), g.showPercentage),
        showBookTimeLeft = prefs.getBoolean(bookKey(bookId, KEY_STATUS_SHOW_BOOK_TIME), g.showBookTimeLeft),
    )
}

fun saveGlobalReaderControlsSettings(prefs: SharedPreferences, s: ReaderControlsSettings) {
    prefs.edit()
        .putBoolean(KEY_RIGHT_SLIDE_VOLUME, s.rightSlideVolume)
        .putBoolean(KEY_LEFT_SLIDE_BRIGHTNESS, s.leftSlideBrightness)
        .putBoolean(KEY_VOLUME_KEYS_PAGE_TURN, s.volumeKeysPageTurn)
        .putBoolean(KEY_VOLUME_UP_FORWARD, s.volumeUpForward)
        .putInt(
            KEY_VOLUME_KEY_ANIMATION_SPEED_MS,
            s.volumeKeyAnimationSpeedMs.coerceIn(
                READER_VOLUME_KEY_ANIMATION_SPEED_MIN_MS,
                READER_VOLUME_KEY_ANIMATION_SPEED_MAX_MS,
            ),
        )
        .putBoolean(KEY_STATUS_SHOW_BATTERY, s.showBattery)
        .putBoolean(KEY_STATUS_SHOW_CHAPTER_TIME, s.showChapterTimeLeft)
        .putBoolean(KEY_STATUS_SHOW_TIME, s.showTime)
        .putBoolean(KEY_STATUS_SHOW_PAGE_COUNT, s.showPageCount)
        .putBoolean(KEY_STATUS_SHOW_PERCENTAGE, s.showPercentage)
        .putBoolean(KEY_STATUS_SHOW_BOOK_TIME, s.showBookTimeLeft)
        .apply()
}

fun saveBookReaderControlsSettings(prefs: SharedPreferences, bookId: Long, s: ReaderControlsSettings) {
    prefs.edit()
        .putBoolean(bookKey(bookId, KEY_RIGHT_SLIDE_VOLUME), s.rightSlideVolume)
        .putBoolean(bookKey(bookId, KEY_LEFT_SLIDE_BRIGHTNESS), s.leftSlideBrightness)
        .putBoolean(bookKey(bookId, KEY_VOLUME_KEYS_PAGE_TURN), s.volumeKeysPageTurn)
        .putBoolean(bookKey(bookId, KEY_VOLUME_UP_FORWARD), s.volumeUpForward)
        .putInt(
            bookKey(bookId, KEY_VOLUME_KEY_ANIMATION_SPEED_MS),
            s.volumeKeyAnimationSpeedMs.coerceIn(
                READER_VOLUME_KEY_ANIMATION_SPEED_MIN_MS,
                READER_VOLUME_KEY_ANIMATION_SPEED_MAX_MS,
            ),
        )
        .putBoolean(bookKey(bookId, KEY_STATUS_SHOW_BATTERY), s.showBattery)
        .putBoolean(bookKey(bookId, KEY_STATUS_SHOW_CHAPTER_TIME), s.showChapterTimeLeft)
        .putBoolean(bookKey(bookId, KEY_STATUS_SHOW_TIME), s.showTime)
        .putBoolean(bookKey(bookId, KEY_STATUS_SHOW_PAGE_COUNT), s.showPageCount)
        .putBoolean(bookKey(bookId, KEY_STATUS_SHOW_PERCENTAGE), s.showPercentage)
        .putBoolean(bookKey(bookId, KEY_STATUS_SHOW_BOOK_TIME), s.showBookTimeLeft)
        .apply()
}

fun clearBookReaderControlsSettings(prefs: SharedPreferences, bookId: Long) {
    prefs.edit()
        .remove(bookKey(bookId, KEY_RIGHT_SLIDE_VOLUME))
        .remove(bookKey(bookId, KEY_LEFT_SLIDE_BRIGHTNESS))
        .remove(bookKey(bookId, KEY_VOLUME_KEYS_PAGE_TURN))
        .remove(bookKey(bookId, KEY_VOLUME_UP_FORWARD))
        .remove(bookKey(bookId, KEY_VOLUME_KEY_ANIMATION_SPEED_MS))
        .remove(bookKey(bookId, KEY_STATUS_SHOW_BATTERY))
        .remove(bookKey(bookId, KEY_STATUS_SHOW_CHAPTER_TIME))
        .remove(bookKey(bookId, KEY_STATUS_SHOW_TIME))
        .remove(bookKey(bookId, KEY_STATUS_SHOW_PAGE_COUNT))
        .remove(bookKey(bookId, KEY_STATUS_SHOW_PERCENTAGE))
        .remove(bookKey(bookId, KEY_STATUS_SHOW_BOOK_TIME))
        .apply()
}

