package com.example.mntnode.recommendation

import com.example.mntnode.data.BookEntity
import com.example.mntnode.epub.EpubOpfParser
import java.io.File
import java.util.zip.ZipFile

data class BookMetadataSignals(
    /** Lowercase normalized author for equality, or null if unknown. */
    val authorKey: String?,
    /** Lowercase subject/genre tags from EPUB metadata. */
    val subjectKeys: Set<String>,
    val format: String,
)

object BookMetadataResolver {

    fun resolve(book: BookEntity): BookMetadataSignals {
        val file = File(book.localPath)
        if (!file.exists()) {
            return BookMetadataSignals(null, emptySet(), book.format.lowercase())
        }
        return when (book.format.lowercase()) {
            "epub" -> readEpub(file, book.format.lowercase())
            "pdf" -> readPdf(file)
            else -> BookMetadataSignals(null, emptySet(), book.format.lowercase())
        }
    }

    private fun readEpub(file: File, format: String): BookMetadataSignals = runCatching {
        ZipFile(file).use { zip ->
            val pkg = EpubOpfParser.readPackage(zip) ?: return@runCatching BookMetadataSignals(null, emptySet(), format)
            val author = pkg.creators.firstOrNull()?.normalizeAuthorKey()
            val subs = pkg.subjects.map { it.lowercase().trim() }.filter { it.isNotBlank() }.toSet()
            BookMetadataSignals(author, subs, format)
        }
    }.getOrElse { BookMetadataSignals(null, emptySet(), format) }

    private fun readPdf(file: File): BookMetadataSignals {
        val (a, _) = readPdfAuthorCreator(file)
        return BookMetadataSignals(a?.normalizeAuthorKey(), emptySet(), "pdf")
    }

    private fun readPdfAuthorCreator(file: File): Pair<String?, String?> = runCatching {
        val text = file.inputStream().buffered().use { it.readBytes().toString(Charsets.ISO_8859_1) }
        val author = Regex("""/Author\s*\(([^)]{1,300})\)""").find(text)?.groupValues?.getOrNull(1)?.trim()
        val creator = Regex("""/Creator\s*\(([^)]{1,300})\)""").find(text)?.groupValues?.getOrNull(1)?.trim()
        author to creator
    }.getOrElse { null to null }

    private fun String.normalizeAuthorKey(): String =
        lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("by ")
}
