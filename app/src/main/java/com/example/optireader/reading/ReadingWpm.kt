package com.example.optireader.reading

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlin.math.max
import kotlin.math.min
/**
 * Reading speed (WPM) defaults, persistence, and session tracking that excludes slider scrubbing
 * and unnaturally long pauses on one page/position.
 */
object ReadingWpm {
    const val PREFS_NAME = "reader_stats"
    const val KEY_AVG_WPM_USER = "avg_wpm_user"
    const val PREFIX_AVG_WPM_BOOK = "avg_wpm_book_"

    /** Default WPM when the user has never finished calibrating on any book. */
    const val DEFAULT_WPM = 250f

    const val WPM_MIN = 50f
    const val WPM_MAX = 1000f

    /** IQR fences for trimming outliers when averaging book-level WPMs. */
    fun trimmedMeanBookWpms(values: List<Float>): Float {
        val v = values.filter { it > 1f }.map { it.coerceIn(WPM_MIN, WPM_MAX) }.sorted()
        if (v.isEmpty()) return DEFAULT_WPM
        if (v.size == 1) return v[0]
        val q1 = v[v.size / 4]
        val q3 = v[v.size * 3 / 4]
        val iqr = (q3 - q1).coerceAtLeast(1f)
        val lo = q1 - 1.5f * iqr
        val hi = q3 + 1.5f * iqr
        val trimmed = v.filter { it in lo..hi }
        val use = if (trimmed.isNotEmpty()) trimmed else v
        return (use.sum() / use.size).coerceIn(WPM_MIN, WPM_MAX)
    }

    fun collectBookWpms(prefs: SharedPreferences): List<Float> =
        prefs.all.keys
            .filter { it.startsWith(PREFIX_AVG_WPM_BOOK) }
            .mapNotNull { key ->
                prefs.getFloat(key, 0f).takeIf { it > 1f }
            }

    /**
     * Starting WPM prior for a book: [DEFAULT_WPM] if no other books have data; otherwise
     * trimmed mean of all other books' stored averages (outliers removed).
     */
    fun baselineWpmForNewBook(prefs: SharedPreferences, excludeBookId: Long): Float {
        val others = prefs.all.keys
            .mapNotNull { key ->
                if (!key.startsWith(PREFIX_AVG_WPM_BOOK)) return@mapNotNull null
                val id = key.removePrefix(PREFIX_AVG_WPM_BOOK).toLongOrNull() ?: return@mapNotNull null
                if (id == excludeBookId) return@mapNotNull null
                prefs.getFloat(key, 0f).takeIf { it > 1f }
            }
        return if (others.isEmpty()) DEFAULT_WPM else trimmedMeanBookWpms(others)
    }

    fun ensureBookWpmSeeded(context: Context, bookId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "$PREFIX_AVG_WPM_BOOK$bookId"
        if (!prefs.contains(key)) {
            val seed = baselineWpmForNewBook(prefs, bookId)
            prefs.edit().putFloat(key, seed).apply()
        }
    }

    fun recomputeAndStoreGlobalUserWpm(prefs: SharedPreferences) {
        val mean = trimmedMeanBookWpms(collectBookWpms(prefs))
        prefs.edit().putFloat(KEY_AVG_WPM_USER, mean).apply()
    }

    /**
     * Updates book EMA and global trimmed mean after a new session WPM sample.
     */
    fun persistSessionSample(prefs: SharedPreferences, bookId: Long, sessionWpm: Float) {
        if (sessionWpm <= 1f) return
        val clamped = sessionWpm.coerceIn(WPM_MIN, WPM_MAX)
        val key = "$PREFIX_AVG_WPM_BOOK$bookId"
        val prev = prefs.getFloat(key, 0f)
        val next = if (prev <= 1f) clamped else (prev * 0.92f + clamped * 0.08f).coerceIn(WPM_MIN, WPM_MAX)
        prefs.edit().putFloat(key, next).apply()
        recomputeAndStoreGlobalUserWpm(prefs)
    }
}

/**
 * Tracks "valid" reading seconds (excludes slider scrubbing and long idle pauses on one unit).
 */
class ReadingSpeedTracker(
    totalWords: Int,
    totalUnits: Int,
    seedWpm: Float,
) {
    private val wordsPerUnit: Float =
        (totalWords.toFloat() / max(1, totalUnits)).coerceAtLeast(1f)

    private var emaDwellSec: Float =
        max(15f, 60f * wordsPerUnit / seedWpm.coerceIn(ReadingWpm.WPM_MIN, ReadingWpm.WPM_MAX))

    private var scrubbing: Boolean = false
    private var lastUnitIndex: Int = -1
    private var pageStartElapsedMs: Long = SystemClock.elapsedRealtime()
    private var validReadSeconds: Long = 0

    fun setScrubbing(active: Boolean) {
        scrubbing = active
        if (!active) {
            pageStartElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Call when the discrete reading unit changes (page index, global position index, or spine index).
     */
    fun onUnitIndexChanged(unitIndex: Int) {
        val now = SystemClock.elapsedRealtime()
        if (lastUnitIndex >= 0 && unitIndex != lastUnitIndex) {
            val dwellSec = (now - pageStartElapsedMs) / 1000f
            val cap = dwellCapSec()
            val eff = min(dwellSec, cap)
            emaDwellSec = (emaDwellSec * 0.85f + eff * 0.15f).coerceIn(8f, 900f)
        }
        lastUnitIndex = unitIndex
        pageStartElapsedMs = now
    }

    fun onTickSecond() {
        if (scrubbing) return
        val now = SystemClock.elapsedRealtime()
        val elapsedOnPage = (now - pageStartElapsedMs) / 1000f
        val cap = dwellCapSec()
        if (elapsedOnPage <= cap) {
            validReadSeconds++
        }
    }

    private fun dwellCapSec(): Float =
        max(45f, min(3f * emaDwellSec, 600f))

    fun validReadSeconds(): Long = validReadSeconds

    fun sessionWpm(wordsReadSoFar: Int): Float {
        val minutes = validReadSeconds / 60f
        if (minutes < 0.05f || wordsReadSoFar <= 0) return 0f
        return (wordsReadSoFar / minutes).coerceIn(ReadingWpm.WPM_MIN, ReadingWpm.WPM_MAX)
    }
}
