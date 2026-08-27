package com.example.optireader.ui

import android.content.Context

/** Persisted total seconds spent in the reader for a book (all sessions). */
object ReaderReadingTime {
    private fun key(bookId: Long) = "book_total_reading_seconds_$bookId"

    fun loadTotalSeconds(context: Context, bookId: Long): Long =
        context.getSharedPreferences("reader_stats", Context.MODE_PRIVATE).getLong(key(bookId), 0L)

    fun saveTotalSeconds(context: Context, bookId: Long, seconds: Long) {
        context.getSharedPreferences("reader_stats", Context.MODE_PRIVATE).edit()
            .putLong(key(bookId), seconds).apply()
    }
}
