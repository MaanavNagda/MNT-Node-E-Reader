package com.example.mntnode.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

internal data class BookmarkEntry(
    val id: Long,
    val format: String,
    val createdAtMillis: Long,
    val progress01: Float,
    /** 0-based PDF page index, or null for EPUB. */
    val pdfPageIndex: Int?,
    /** Serialized Readium locator for EPUB; null for PDF. */
    val locatorJsonString: String?,
    val previewText: String,
)

private const val PREFS_NAME = "book_bookmarks"
private fun key(bookId: Long) = "marks_$bookId"

internal const val KEY_BOOKMARK_ICON_COLOR_ARGB = "bookmark_icon_color_argb"
private const val DEFAULT_BOOKMARK_ICON_ARGB = 0xFFFFC107.toInt()

internal fun loadBookmarkIconColorArgb(readerPrefs: android.content.SharedPreferences): Int {
    if (!readerPrefs.contains(KEY_BOOKMARK_ICON_COLOR_ARGB)) return DEFAULT_BOOKMARK_ICON_ARGB
    return readerPrefs.getInt(KEY_BOOKMARK_ICON_COLOR_ARGB, DEFAULT_BOOKMARK_ICON_ARGB)
}

internal fun saveBookmarkIconColorArgb(readerPrefs: android.content.SharedPreferences, argb: Int) {
    readerPrefs.edit().putInt(KEY_BOOKMARK_ICON_COLOR_ARGB, argb).apply()
}

internal object BookmarkStore {

    fun load(context: Context, bookId: Long): List<BookmarkEntry> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key(bookId), null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(parseEntry(o))
                }
            }
        }.getOrElse { emptyList() }
    }

    fun getLatest(context: Context, bookId: Long): BookmarkEntry? =
        load(context, bookId).maxByOrNull { it.createdAtMillis }

    fun addPdf(
        context: Context,
        bookId: Long,
        pdfPageIndex: Int,
        progress01: Float,
        previewText: String,
    ) {
        val entry = BookmarkEntry(
            id = System.currentTimeMillis(),
            format = "pdf",
            createdAtMillis = System.currentTimeMillis(),
            progress01 = progress01.coerceIn(0f, 1f),
            pdfPageIndex = pdfPageIndex.coerceAtLeast(0),
            locatorJsonString = null,
            previewText = previewText.trim().ifBlank { "Page ${pdfPageIndex + 1}" },
        )
        mutate(context, bookId) { arr -> arr.put(entry.toJson()) }
    }

    fun addEpub(
        context: Context,
        bookId: Long,
        locator: Locator,
        progress01: Float,
    ) {
        val preview = locatorPreviewText(locator)
        val entry = BookmarkEntry(
            id = System.currentTimeMillis(),
            format = "epub",
            createdAtMillis = System.currentTimeMillis(),
            progress01 = progress01.coerceIn(0f, 1f),
            pdfPageIndex = null,
            locatorJsonString = locator.toJSON().toString(),
            previewText = preview,
        )
        mutate(context, bookId) { arr -> arr.put(entry.toJson()) }
    }

    private fun mutate(context: Context, bookId: Long, block: (JSONArray) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(key(bookId), null)
        val arr = if (existing.isNullOrBlank()) JSONArray() else runCatching { JSONArray(existing) }.getOrElse { JSONArray() }
        block(arr)
        prefs.edit().putString(key(bookId), arr.toString()).apply()
    }

    private fun parseEntry(o: JSONObject): BookmarkEntry =
        BookmarkEntry(
            id = o.optLong("id", System.currentTimeMillis()),
            format = o.optString("format", "pdf"),
            createdAtMillis = o.optLong("createdAtMillis", o.optLong("id", 0L)),
            progress01 = o.optDouble("progress01", 0.0).toFloat().coerceIn(0f, 1f),
            pdfPageIndex = if (o.has("pdfPageIndex") && !o.isNull("pdfPageIndex")) o.optInt("pdfPageIndex") else null,
            locatorJsonString = o.optString("locatorJson", null)?.takeIf { it.isNotBlank() },
            previewText = o.optString("previewText", ""),
        )

    private fun BookmarkEntry.toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("format", format)
            put("createdAtMillis", createdAtMillis)
            put("progress01", progress01.toDouble())
            pdfPageIndex?.let { put("pdfPageIndex", it) }
            locatorJsonString?.let { put("locatorJson", it) }
            put("previewText", previewText)
        }
}

internal fun locatorPreviewText(locator: Locator): String {
    val text = locator.text
    val s = buildString {
        text?.before?.let { append(it) }
        text?.highlight?.let { append(it) }
        text?.after?.let { append(it) }
    }.trim().replace(Regex("\\s+"), " ")
    if (s.isNotBlank()) {
        return if (s.length > 140) s.take(137) + "…" else s
    }
    return "Bookmark"
}

internal fun bookmarkMatchesPdfPage(entry: BookmarkEntry, pageIndex: Int): Boolean =
    entry.format == "pdf" && entry.pdfPageIndex == pageIndex

internal fun bookmarkMatchesEpubLocator(entry: BookmarkEntry, locatorJson: String?): Boolean {
    if (entry.format != "epub" || entry.locatorJsonString.isNullOrBlank()) return false
    if (locatorJson.isNullOrBlank()) return false
    return entry.locatorJsonString == locatorJson
}

/** Sort: PDF by page, EPUB by progress, then newest first within ties. */
internal fun sortedBookmarksForDisplay(entries: List<BookmarkEntry>): List<BookmarkEntry> =
    entries.sortedWith(
        compareBy<BookmarkEntry> { e ->
            when (e.format) {
                "pdf" -> e.pdfPageIndex ?: 0
                else -> (e.progress01 * 1_000_000).toInt()
            }
        }.thenByDescending { it.createdAtMillis },
    )
