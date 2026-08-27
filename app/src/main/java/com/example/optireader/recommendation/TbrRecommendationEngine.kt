package com.example.optireader.recommendation

import com.example.optireader.data.BookEntity
import com.example.optireader.data.preferences.RecommendationAnchor
import kotlin.math.min

/**
 * Ranks TBR books using completed-book signals, library reading history with ratings,
 * time-on-TBR (older imports first), and a user-chosen anchor (author/genre preference).
 */
object TbrRecommendationEngine {

    fun pickTop3(
        completed: BookEntity,
        completedSignals: BookMetadataSignals,
        readBooks: List<BookEntity>,
        readSignals: Map<Long, BookMetadataSignals>,
        tbrBooks: List<BookEntity>,
        tbrSignals: Map<Long, BookMetadataSignals>,
        anchor: RecommendationAnchor,
        nowMillis: Long,
    ): List<BookEntity> {
        val completedId = completed.id
        val candidates = tbrBooks.filter { it.id != completedId }
        if (candidates.isEmpty()) return emptyList()

        val authorRatingAvg = averageRatingByAuthor(readBooks, readSignals)

        val scored = candidates.map { book ->
            val sig = tbrSignals[book.id] ?: BookMetadataSignals(null, emptySet(), book.format.lowercase())
            val score = scoreBook(
                book = book,
                sig = sig,
                completedSignals = completedSignals,
                anchor = anchor,
                nowMillis = nowMillis,
                authorRatingAvg = authorRatingAvg,
            )
            book to score
        }

        return scored
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.id }
            .take(3)
    }

    private fun averageRatingByAuthor(
        readBooks: List<BookEntity>,
        readSignals: Map<Long, BookMetadataSignals>,
    ): Map<String, Float> {
        val byAuthor = mutableMapOf<String, MutableList<Float>>()
        for (b in readBooks) {
            val r = b.rating10 ?: continue
            val key = readSignals[b.id]?.authorKey ?: continue
            byAuthor.getOrPut(key) { mutableListOf() }.add(r)
        }
        return byAuthor.mapValues { (_, list) -> list.average().toFloat() }
    }

    private fun scoreBook(
        book: BookEntity,
        sig: BookMetadataSignals,
        completedSignals: BookMetadataSignals,
        anchor: RecommendationAnchor,
        nowMillis: Long,
        authorRatingAvg: Map<String, Float>,
    ): Float {
        var s = 0f

        val daysWaiting = ((nowMillis - book.importedAtMillis).coerceAtLeast(0L) / 86_400_000f)
        s += min(daysWaiting, 730f) * 1.6f

        sig.authorKey?.let { ak ->
            authorRatingAvg[ak]?.let { avg -> s += (avg - 5f) * 2.2f }
        }

        if (sig.format == completedSignals.format) s += 10f

        when (anchor) {
            RecommendationAnchor.SameAuthor -> {
                if (sig.authorKey != null && completedSignals.authorKey != null) {
                    if (sig.authorKey == completedSignals.authorKey) s += 130f
                    else s -= 15f
                }
            }
            RecommendationAnchor.SameGenre -> {
                val overlap = sig.subjectKeys.intersect(completedSignals.subjectKeys)
                s += overlap.size * 50f
                if (completedSignals.subjectKeys.isNotEmpty() && overlap.isEmpty()) s -= 12f
            }
            RecommendationAnchor.DifferentAuthor -> {
                if (sig.authorKey != null && completedSignals.authorKey != null) {
                    if (sig.authorKey != completedSignals.authorKey) s += 85f
                    else s -= 70f
                }
            }
            RecommendationAnchor.DifferentGenre -> {
                if (completedSignals.subjectKeys.isNotEmpty()) {
                    val overlap = sig.subjectKeys.intersect(completedSignals.subjectKeys)
                    if (overlap.isEmpty()) s += 75f
                    else s -= overlap.size * 28f
                } else {
                    if (sig.format != completedSignals.format) s += 30f
                }
            }
        }

        s += book.id * 1e-6f
        return s
    }
}
