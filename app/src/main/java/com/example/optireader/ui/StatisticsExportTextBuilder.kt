package com.example.optireader.ui

import android.content.Context
import com.example.optireader.data.BookEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object StatisticsExportTextBuilder {

    fun buildExport(
        context: Context,
        mainBooks: List<BookEntity>,
        tbrBooks: List<BookEntity>,
        folderNamesById: Map<Long, String>,
    ): StatisticsExportPayload {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val header = buildString {
            appendLine("Optireader reading statistics export")
            appendLine("Generated: $ts")
            appendLine()
            appendLine("=".repeat(60))
            appendLine("LIBRARY — PER-BOOK STATS")
            appendLine("=".repeat(60))
            appendLine()
            if (mainBooks.isEmpty()) {
                appendLine("(No books on the main library shelf.)")
                appendLine()
            }
        }

        val segments = mutableListOf<PdfSegment>()
        segments += PdfSegment.Text(header)

        val fullText = StringBuilder(header)

        mainBooks.forEachIndexed { index, book ->
            val section = buildBookSection(context, book, folderNamesById)
            segments += PdfSegment.Text(section.text)
            fullText.append(section.text)
            val graphPts = section.graphPoints
            if (graphPts.size >= 2) {
                segments += PdfSegment.ProgressGraph(book.title, graphPts)
            }
            if (index < mainBooks.lastIndex) {
                // spacing between books already ends section.text with blank line
            }
        }

        val tbrSection = buildTbrSection(tbrBooks)
        segments += PdfSegment.Text(tbrSection)
        fullText.append(tbrSection)

        return StatisticsExportPayload(
            textForTxtFile = fullText.toString().trimEnd() + "\n",
            pdfDocument = PdfStatisticsDocument(segments),
        )
    }

    private fun buildTbrSection(tbrBooks: List<BookEntity>): String = buildString {
        appendLine("=".repeat(60))
        appendLine("TBR")
        appendLine("=".repeat(60))
        appendLine()
        if (tbrBooks.isEmpty()) {
            appendLine("(TBR is empty.)")
        } else {
            tbrBooks.forEach { book ->
                appendLine("- ${book.title}")
                appendLine("  Path: ${book.localPath}")
                if (!book.sourceAbsolutePath.isNullOrBlank()) {
                    appendLine("  Source (when imported): ${book.sourceAbsolutePath}")
                }
                appendLine("  Format: ${book.format}")
                appendLine()
            }
        }
    }

    private data class BookSectionResult(
        val text: String,
        val graphPoints: List<Pair<Long, Float>>,
    )

    private fun buildBookSection(
        context: Context,
        book: BookEntity,
        folderNamesById: Map<Long, String>,
    ): BookSectionResult {
        val sb = StringBuilder()
        val fid = book.folderId
        val folderLine = if (fid != null) {
            val name = folderNamesById[fid]?.trim().orEmpty()
            if (name.isNotEmpty()) "Folder: $name (id=$fid)" else "Folder id: $fid"
        } else {
            "Shelf: main (standalone)"
        }
        sb.appendLine("Title: ${book.title}")
        sb.appendLine("Book id: ${book.id}")
        sb.appendLine(folderLine)
        sb.appendLine("Format: ${book.format}")
        sb.appendLine("Local path: ${book.localPath}")
        if (!book.sourceAbsolutePath.isNullOrBlank()) {
            sb.appendLine("Source path: ${book.sourceAbsolutePath}")
        }
        val pct = (book.readProgress01 * 100f).roundToInt().coerceIn(0, 100)
        sb.appendLine("Reading progress: $pct%")
        sb.appendLine("Rating (0–10): ${book.rating10?.let { String.format(Locale.US, "%.1f", it) } ?: "(none)"}")

        val totalSecs = ReaderReadingTime.loadTotalSeconds(context, book.id)
        sb.appendLine("Total time in reader: ${formatDuration(totalSecs)}")

        val hasStatsFile = BookStatsStore.hasPersistedStats(context, book.id)
        val snap = if (hasStatsFile) {
            BookStatsStore.load(context, book.id)
        } else {
            BookStatsSnapshot(emptyList(), emptyList(), emptyList(), "")
        }
        val sessions = snap.readSessions
        val readCount = sessions.size
        sb.appendLine("Number of reads (sessions): $readCount")

        sb.appendLine("Reading speed:")
        sb.appendLine(readingSpeedLines(sessions, book.readProgress01, totalSecs))

        if (sessions.isEmpty()) {
            sb.appendLine("Reads (by session):")
            sb.appendLine("  (No read session history.)")
        } else {
            sb.appendLine("Reads (by session) — date started / date ended:")
            sessions.forEachIndexed { i, session ->
                val end = session.dateFinished?.takeIf { it.isNotBlank() } ?: "(ongoing)"
                sb.appendLine("  ${i + 1}. Started: ${session.dateStarted}  Ended: $end")
            }
        }
        sb.appendLine()

        val graphPoints = mergedProgressPoints(sessions)

        if (snap.overallNotes.isNotBlank() || snap.highlights.isNotEmpty() || snap.inBookNotes.isNotEmpty()) {
            sb.appendLine("— More detail —")
            if (snap.overallNotes.isNotBlank()) {
                sb.appendLine("Overall notes: ${snap.overallNotes}")
            }
            if (snap.highlights.isNotEmpty()) {
                sb.appendLine("Highlights (${snap.highlights.size}):")
                snap.highlights.forEach { h ->
                    sb.appendLine("  * ${h.text.replace("\n", " ")}")
                }
            }
            if (snap.inBookNotes.isNotEmpty()) {
                sb.appendLine("In-book notes (${snap.inBookNotes.size}):")
                snap.inBookNotes.forEach { n ->
                    sb.appendLine("  Anchor: ${n.anchorText.replace("\n", " ")}")
                    sb.appendLine("  Note: ${n.note.replace("\n", " ")}")
                }
            }
            sb.appendLine()
        } else if (!hasStatsFile) {
            sb.appendLine("(No extended stats file for this book — only shelf fields above.)")
            sb.appendLine()
        }

        return BookSectionResult(sb.toString(), graphPoints)
    }

    private fun mergedProgressPoints(sessions: List<ReadSessionRecord>): List<Pair<Long, Float>> {
        val all = sessions.flatMap { it.progressPoints }
        if (all.isEmpty()) return emptyList()
        return all
            .sortedBy { it.atMillis }
            .map { it.atMillis to it.progress01 }
    }

    private fun readingSpeedLines(
        sessions: List<ReadSessionRecord>,
        bookProgress01: Float,
        totalReaderSeconds: Long,
    ): String {
        val rates = sessions.mapNotNull { sessionProgressRatePercentPerHour(it) }
        val lines = mutableListOf<String>()
        if (rates.isNotEmpty()) {
            val avg = rates.average()
            lines += "  From timed progress within reads (avg): ${String.format(Locale.US, "%.1f", avg)}% of book per hour"
        }
        if (totalReaderSeconds >= 120L && bookProgress01 > 0.01f) {
            val hours = totalReaderSeconds / 3600.0
            val pctPerHr = (bookProgress01 / hours) * 100.0
            lines += "  From total reader time vs current progress: ${String.format(Locale.US, "%.1f", pctPerHr)}% of book per hour"
        }
        if (lines.isEmpty()) {
            lines += "  (Not enough timed progress samples — need 2+ points at least 1 minute apart in a session, or 2+ minutes in reader with progress.)"
        }
        return lines.joinToString("\n")
    }

    /** Estimated % of book completed per hour from first→last sample in a session. */
    private fun sessionProgressRatePercentPerHour(session: ReadSessionRecord): Double? {
        val pts = session.progressPoints.sortedBy { it.atMillis }
        if (pts.size < 2) return null
        val dt = pts.last().atMillis - pts.first().atMillis
        if (dt < 60_000L) return null
        val dp = pts.last().progress01 - pts.first().progress01
        if (dp <= 1e-5f) return null
        val hours = dt / 3_600_000.0
        return (dp / hours) * 100.0
    }

    private fun formatDuration(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "0s"
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0 || h > 0) append("${m}m ")
            append("${s}s")
        }.trim()
    }
}
