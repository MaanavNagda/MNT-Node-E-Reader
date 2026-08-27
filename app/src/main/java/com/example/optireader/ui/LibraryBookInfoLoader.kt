package com.example.optireader.ui

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.optireader.data.BookEntity
import com.example.optireader.epub.EpubOpfParser
import com.example.optireader.reading.ReadingWpm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.roundToInt

internal data class LibraryBookStatsContext(
    val book: BookEntity,
    val info: BookInfoUiData,
    val totalReadingSeconds: Long,
    val historyDays: Int,
    val avgWpmUser: Float,
    val avgWpmBook: Float,
)

internal suspend fun loadLibraryBookStatsContext(context: Context, book: BookEntity): LibraryBookStatsContext =
    withContext(Dispatchers.IO) {
        val file = File(book.localPath)
        val (words, chars) = when (book.format.lowercase()) {
            "pdf" -> estimatePdfWordsChars(file)
            "epub" -> estimateEpubWordsChars(file)
            else -> 0 to 0
        }
        val totalUnits = when (book.format.lowercase()) {
            "pdf" -> pdfPageCount(file).coerceAtLeast(1)
            else -> 1
        }.coerceAtLeast(1)
        val (author, creator) = when (book.format.lowercase()) {
            "pdf" -> readPdfAuthorCreator(file)
            else -> null to null
        }
        val titleMeta = when (book.format.lowercase()) {
            "epub" -> readEpubTitleFromZip(file) ?: book.title
            else -> book.title
        }
        val info = BookInfoUiData(
            chapters = emptyList(),
            chapterPageIndexBySpine = emptyMap(),
            title = titleMeta,
            author = author ?: "Unknown",
            creator = creator ?: "Unknown",
            fileName = file.name,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            totalUnits = totalUnits,
            currentUnit = ((book.readProgress01.coerceIn(0f, 1f)) * totalUnits).roundToInt()
                .coerceIn(1, totalUnits),
            unitDisplay = BookInfoUnitDisplay.PAGES,
            wordsCount = words,
            charsCount = chars,
        )
        val prefs = context.getSharedPreferences(ReadingWpm.PREFS_NAME, Context.MODE_PRIVATE)
        ReadingWpm.ensureBookWpmSeeded(context, book.id)
        val bookKey = "book_days_${book.id}"
        val daySet = prefs.getStringSet(bookKey, emptySet()) ?: emptySet()
        LibraryBookStatsContext(
            book = book,
            info = info,
            totalReadingSeconds = ReaderReadingTime.loadTotalSeconds(context, book.id),
            historyDays = daySet.size.coerceAtLeast(1),
            avgWpmUser = prefs.getFloat(ReadingWpm.KEY_AVG_WPM_USER, 0f),
            avgWpmBook = prefs.getFloat("${ReadingWpm.PREFIX_AVG_WPM_BOOK}${book.id}", 0f),
        )
    }

private fun readEpubTitleFromZip(file: File): String? = runCatching {
    ZipFile(file).use { zip ->
        EpubOpfParser.readPackage(zip)?.title?.trim()?.takeIf { it.isNotEmpty() }
    }
}.getOrNull()

private fun readPdfAuthorCreator(file: File): Pair<String?, String?> = runCatching {
    val text = file.inputStream().buffered().use { it.readBytes().toString(Charsets.ISO_8859_1) }
    val author = Regex("""/Author\s*\(([^)]{1,300})\)""").find(text)?.groupValues?.getOrNull(1)?.trim()
    val creator = Regex("""/Creator\s*\(([^)]{1,300})\)""").find(text)?.groupValues?.getOrNull(1)?.trim()
    author to creator
}.getOrElse { null to null }

private fun estimatePdfWordsChars(file: File): Pair<Int, Int> = runCatching {
    val text = file.inputStream().buffered().use { it.readBytes().toString(Charsets.ISO_8859_1) }
    val chunks = Regex("""\(([^\)]{2,200})\)""").findAll(text).map { it.groupValues[1] }.toList()
    val plain = chunks.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    val words = if (plain.isBlank()) 0 else plain.split(' ').size
    words to plain.length
}.getOrElse { 0 to 0 }

private fun estimateEpubWordsChars(file: File): Pair<Int, Int> = runCatching {
    ZipFile(file).use { zip ->
        val pkg = EpubOpfParser.readPackage(zip) ?: return@use 0 to 0
        val content = buildString {
            pkg.spinePathsInZip().forEach { path ->
                val e = zip.getEntry(path) ?: return@forEach
                append(zip.getInputStream(e).bufferedReader().use { it.readText() })
                append('\n')
            }
        }
        val plain = content.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        val words = if (plain.isBlank()) 0 else plain.split(' ').size
        words to plain.length
    }
}.getOrElse { 0 to 0 }

private fun pdfPageCount(file: File): Int = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { it.pageCount }
    }
}.getOrElse { 0 }
