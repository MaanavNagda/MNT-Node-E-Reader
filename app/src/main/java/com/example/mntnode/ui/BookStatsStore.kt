package com.example.mntnode.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal data class ProgressPoint(
    val day: String,
    val progress01: Float,
    /** Wall-clock time of this sample for chart x-axis (epoch millis). */
    val atMillis: Long,
)
internal data class HighlightRecord(
    val text: String,
    val paragraph: String,
    val createdAtMillis: Long,
)
internal data class InBookNoteRecord(
    val anchorText: String,
    val note: String,
    val createdAtMillis: Long,
)
internal data class ReadSessionRecord(
    val dateStarted: String,
    val dateFinished: String?,
    val progressPoints: List<ProgressPoint>,
)
internal data class BookStatsSnapshot(
    val readSessions: List<ReadSessionRecord>,
    val highlights: List<HighlightRecord>,
    val inBookNotes: List<InBookNoteRecord>,
    val overallNotes: String,
)

internal object BookStatsStore {
    private const val PREFS = "book_stats"
    /** New point at least this often so the chart has a time spread (3 minutes). */
    private const val PROGRESS_APPEND_INTERVAL_MS = 3L * 60 * 1000
    /** New point when progress jumps by this fraction (2%). */
    private const val PROGRESS_APPEND_DELTA = 0.02f
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private fun key(bookId: Long) = "book_stats_$bookId"

    fun hasPersistedStats(context: Context, bookId: Long): Boolean {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(bookId), null)
        return !raw.isNullOrBlank()
    }

    fun load(context: Context, bookId: Long): BookStatsSnapshot {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(bookId), null)
            ?: return BookStatsSnapshot(emptyList(), emptyList(), emptyList(), "")
        return runCatching {
            val o = JSONObject(raw)
            migrateLegacyReadSessions(o)
            BookStatsSnapshot(
                readSessions = parseReadSessions(o.optJSONArray("readSessions")),
                highlights = parseHighlights(o.optJSONArray("highlights")),
                inBookNotes = parseNotes(o.optJSONArray("notes")),
                overallNotes = o.optString("overallNotes"),
            )
        }.getOrElse {
            BookStatsSnapshot(emptyList(), emptyList(), emptyList(), "")
        }
    }

    fun recordProgress(context: Context, bookId: Long, progress01: Float, nowMillis: Long = System.currentTimeMillis()) {
        mutate(context, bookId) { o ->
            migrateLegacyReadSessions(o)
            val p = progress01.coerceIn(0f, 1f)
            val day = formatDay(nowMillis)
            if (p > 0f) {
                ensureSessionForActiveReading(o, day, p)
            }
            val rs = o.optJSONArray("readSessions") ?: return@mutate
            val activeIdx = findActiveSessionIndex(rs)
            if (activeIdx != null) {
                val session = rs.optJSONObject(activeIdx) ?: return@mutate
                val arr = session.optJSONArray("progress") ?: JSONArray().also { session.put("progress", it) }
                val n = arr.length()
                val lastObj = if (n > 0) arr.optJSONObject(n - 1) else null
                val lastP = lastObj?.optDouble("progress", 0.0)?.toFloat()?.coerceIn(0f, 1f) ?: 0f
                if (!(lastP >= 0.999f && p >= 0.999f)) {
                    val lastT = lastObj?.optLong("atMillis", 0L) ?: 0L
                    val interval = if (lastObj != null) nowMillis - lastT else Long.MAX_VALUE
                    val deltaP = p - lastP
                    val completionEdge = p >= 0.999f && lastP < 0.999f
                    val shouldAppend = lastObj == null ||
                        interval >= PROGRESS_APPEND_INTERVAL_MS ||
                        deltaP >= PROGRESS_APPEND_DELTA ||
                        completionEdge ||
                        p < lastP - 0.0005f
                    if (shouldAppend) {
                        arr.put(
                            JSONObject()
                                .put("day", day)
                                .put("progress", p.toDouble())
                                .put("atMillis", nowMillis),
                        )
                    } else if (p > lastP) {
                        val lo = requireNotNull(lastObj)
                        lo.put("progress", p.toDouble())
                        lo.put("atMillis", nowMillis)
                    }
                }
            }
            if (p >= 0.999f) {
                markLastSessionFinished(o, day)
            }
        }
    }

    fun addHighlight(context: Context, bookId: Long, selectedText: String, paragraph: String?) {
        val clean = selectedText.trim()
        if (clean.isBlank()) return
        mutate(context, bookId) { o ->
            val arr = o.optJSONArray("highlights") ?: JSONArray().also { o.put("highlights", it) }
            arr.put(
                JSONObject()
                    .put("text", clean)
                    .put("paragraph", paragraph?.trim().orEmpty())
                    .put("createdAtMillis", System.currentTimeMillis()),
            )
        }
    }

    fun addInBookNote(context: Context, bookId: Long, anchorText: String, note: String) {
        val n = note.trim()
        if (n.isBlank()) return
        mutate(context, bookId) { o ->
            val arr = o.optJSONArray("notes") ?: JSONArray().also { o.put("notes", it) }
            arr.put(
                JSONObject()
                    .put("anchorText", anchorText.trim())
                    .put("note", n)
                    .put("createdAtMillis", System.currentTimeMillis()),
            )
        }
    }

    fun setOverallNotes(context: Context, bookId: Long, notes: String) {
        mutate(context, bookId) { o -> o.put("overallNotes", notes) }
    }

    /**
     * User confirmed a reread from 100%: clear progress chart for a fresh arc,
     * close the previous read session if still open, start a new session for this reread.
     */
    fun beginRereadSession(context: Context, bookId: Long, nowMillis: Long = System.currentTimeMillis()) {
        mutate(context, bookId) { o ->
            migrateLegacyReadSessions(o)
            val rs = o.optJSONArray("readSessions") ?: JSONArray().also { o.put("readSessions", it) }
            if (rs.length() > 0) {
                val last = rs.optJSONObject(rs.length() - 1)
                if (last != null && last.optString("dateFinished").isBlank()) {
                    last.put("dateFinished", formatDay(nowMillis))
                }
            }
            o.remove("dateStarted")
            o.remove("dateFinished")
            o.remove("progress")
            val day = formatDay(nowMillis)
            rs.put(
                JSONObject()
                    .put("dateStarted", day)
                    .put("dateFinished", "")
                    .put("progress", JSONArray()),
            )
        }
        recordProgress(context, bookId, 0f, nowMillis)
    }

    private fun mutate(context: Context, bookId: Long, block: (JSONObject) -> Unit) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getString(key(bookId), null)
        val o = runCatching { if (start.isNullOrBlank()) JSONObject() else JSONObject(start) }.getOrElse { JSONObject() }
        block(o)
        prefs.edit().putString(key(bookId), o.toString()).apply()
    }

    private fun deepCopyJsonArray(src: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until src.length()) {
            when (val v = src.get(i)) {
                is JSONObject -> out.put(JSONObject(v.toString()))
                is JSONArray -> out.put(deepCopyJsonArray(v))
                else -> out.put(v)
            }
        }
        return out
    }

    /**
     * Build [readSessions] from legacy root fields; move top-level [progress] into the matching session.
     */
    private fun migrateLegacyReadSessions(o: JSONObject) {
        val globalProgress = o.optJSONArray("progress")
        val existing = o.optJSONArray("readSessions")
        if (existing != null && existing.length() > 0) {
            if (globalProgress != null && globalProgress.length() > 0) {
                val first = existing.optJSONObject(0)
                if (first != null) {
                    val inner = first.optJSONArray("progress")
                    if (inner == null || inner.length() == 0) {
                        first.put("progress", deepCopyJsonArray(globalProgress))
                        o.remove("progress")
                    }
                }
            }
            for (i in 0 until existing.length()) {
                val s = existing.optJSONObject(i) ?: continue
                if (!s.has("progress")) {
                    s.put("progress", JSONArray())
                }
            }
            return
        }
        val ds = o.optString("dateStarted").trim().ifBlank { null }
        val df = o.optString("dateFinished").trim().ifBlank { null }
        if (ds == null && df == null && (globalProgress == null || globalProgress.length() == 0)) return
        val arr = JSONArray()
        val sessionObj = JSONObject()
            .put("dateStarted", ds ?: df ?: formatDay(System.currentTimeMillis()))
            .put("dateFinished", df ?: "")
        if (globalProgress != null && globalProgress.length() > 0) {
            sessionObj.put("progress", deepCopyJsonArray(globalProgress))
            o.remove("progress")
        } else {
            sessionObj.put("progress", JSONArray())
        }
        arr.put(sessionObj)
        o.put("readSessions", arr)
    }

    private fun findActiveSessionIndex(rs: JSONArray): Int? {
        if (rs.length() == 0) return null
        for (i in rs.length() - 1 downTo 0) {
            val s = rs.optJSONObject(i) ?: continue
            if (s.optString("dateFinished").isBlank()) return i
        }
        return null
    }

    private fun parseReadSessions(arr: JSONArray?): List<ReadSessionRecord> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val ds = row.optString("dateStarted").trim()
                if (ds.isBlank()) continue
                val df = row.optString("dateFinished").trim().ifBlank { null }
                add(
                    ReadSessionRecord(
                        dateStarted = ds,
                        dateFinished = df,
                        progressPoints = parseProgress(row.optJSONArray("progress")),
                    ),
                )
            }
        }
    }

    private fun ensureSessionForActiveReading(o: JSONObject, dateStartedDay: String, p: Float) {
        val arr = o.optJSONArray("readSessions") ?: JSONArray().also { o.put("readSessions", it) }
        if (arr.length() == 0) {
            arr.put(
                JSONObject()
                    .put("dateStarted", dateStartedDay)
                    .put("dateFinished", "")
                    .put("progress", JSONArray()),
            )
            return
        }
        val last = arr.optJSONObject(arr.length() - 1) ?: return
        val finished = last.optString("dateFinished").isNotBlank()
        if (finished && p < 0.999f) {
            arr.put(
                JSONObject()
                    .put("dateStarted", dateStartedDay)
                    .put("dateFinished", "")
                    .put("progress", JSONArray()),
            )
        }
    }

    private fun markLastSessionFinished(o: JSONObject, finishedDay: String) {
        val arr = o.optJSONArray("readSessions") ?: return
        if (arr.length() == 0) return
        val last = arr.optJSONObject(arr.length() - 1) ?: return
        if (last.optString("dateFinished").isBlank()) {
            last.put("dateFinished", finishedDay)
        }
    }

    private fun parseProgress(arr: JSONArray?): List<ProgressPoint> {
        if (arr == null) return emptyList()
        val zone = ZoneId.systemDefault()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val day = row.optString("day").trim()
                if (day.isBlank()) continue
                val storedMillis = row.optLong("atMillis", 0L)
                val atMillis = if (storedMillis > 0L) {
                    storedMillis
                } else {
                    runCatching {
                        LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
                    }.getOrElse { 0L }
                }
                add(
                    ProgressPoint(
                        day = day,
                        progress01 = row.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                        atMillis = atMillis,
                    ),
                )
            }
        }.sortedBy { it.atMillis }
    }

    private fun parseHighlights(arr: JSONArray?): List<HighlightRecord> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val text = o.optString("text").trim()
                if (text.isBlank()) continue
                add(
                    HighlightRecord(
                        text = text,
                        paragraph = o.optString("paragraph"),
                        createdAtMillis = o.optLong("createdAtMillis", 0L),
                    ),
                )
            }
        }
    }

    private fun parseNotes(arr: JSONArray?): List<InBookNoteRecord> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val note = o.optString("note").trim()
                if (note.isBlank()) continue
                add(
                    InBookNoteRecord(
                        anchorText = o.optString("anchorText"),
                        note = note,
                        createdAtMillis = o.optLong("createdAtMillis", 0L),
                    ),
                )
            }
        }
    }

    private fun formatDay(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFmt)

    /**
     * Seeds [sessionCount] completed read sessions (each finished at 100% progress) for imported books
     * marked as read before (e.g. first-import wizard).
     */
    fun seedCompletedReadSessions(context: Context, bookId: Long, sessionCount: Int) {
        val n = sessionCount.coerceAtLeast(1)
        val now = System.currentTimeMillis()
        mutate(context, bookId) { o ->
            migrateLegacyReadSessions(o)
            val arr = JSONArray()
            for (i in 0 until n) {
                val dayMs = now - (n - 1 - i) * 86_400_000L
                val day = formatDay(dayMs)
                val progressArr = JSONArray()
                progressArr.put(
                    JSONObject()
                        .put("day", day)
                        .put("progress", 1.0)
                        .put("atMillis", dayMs),
                )
                arr.put(
                    JSONObject()
                        .put("dateStarted", day)
                        .put("dateFinished", day)
                        .put("progress", progressArr),
                )
            }
            o.put("readSessions", arr)
        }
    }
}
