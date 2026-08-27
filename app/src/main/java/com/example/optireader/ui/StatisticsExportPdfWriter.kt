package com.example.optireader.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Renders statistics export: monospace text pages plus progress graphs (PDF only).
 */
object StatisticsExportPdfWriter {

    private const val PAGE_WIDTH_PT = 595
    private const val PAGE_HEIGHT_PT = 842
    private const val MARGIN_PT = 40f

    fun write(document: PdfStatisticsDocument, out: OutputStream) {
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 9f
            color = Color.BLACK
        }
        val lineHeight = ceil((bodyPaint.descent() - bodyPaint.ascent()).toDouble()).toFloat() + 2f
        val contentWidth = PAGE_WIDTH_PT - 2 * MARGIN_PT

        val pdf = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun openTextPage() {
            pageNumber++
            val p = pdf.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNumber).create(),
            )
            page = p
            canvas = p.canvas
            y = MARGIN_PT - bodyPaint.ascent()
        }

        fun closeTextPage() {
            page?.let { pdf.finishPage(it) }
            page = null
            canvas = null
        }

        fun drawTextLines(text: String) {
            for (line in wrapLines(text, bodyPaint, contentWidth)) {
                // Must use the current page's canvas after every pagination break; the local
                // canvas reference from an earlier iteration points at a finished page.
                if (canvas == null || y + bodyPaint.descent() > PAGE_HEIGHT_PT - MARGIN_PT) {
                    closeTextPage()
                    openTextPage()
                }
                canvas!!.drawText(line, MARGIN_PT, y, bodyPaint)
                y += lineHeight
            }
        }

        try {
            for (segment in document.segments) {
                when (segment) {
                    is PdfSegment.Text -> drawTextLines(segment.content)
                    is PdfSegment.ProgressGraph -> {
                        if (segment.points.size < 2) continue
                        closeTextPage()
                        pageNumber++
                        val gPage = pdf.startPage(
                            PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNumber).create(),
                        )
                        drawProgressGraph(gPage.canvas, segment.bookTitle, segment.points)
                        pdf.finishPage(gPage)
                    }
                }
            }
            closeTextPage()
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    private fun drawProgressGraph(canvas: Canvas, bookTitle: String, points: List<Pair<Long, Float>>) {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 11f
            color = Color.BLACK
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 8f
            color = Color.DKGRAY
        }
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            strokeWidth = 0.5f
            style = Paint.Style.STROKE
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLUE
            strokeWidth = 2f
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }

        val title = "Reading progress — ${bookTitle.take(56)}${if (bookTitle.length > 56) "…" else ""}"
        canvas.drawText(title, MARGIN_PT, MARGIN_PT - titlePaint.ascent(), titlePaint)

        val chartTop = MARGIN_PT + 28f
        val chartBottom = PAGE_HEIGHT_PT - MARGIN_PT - 40f
        val chartLeft = MARGIN_PT + 32f
        val chartRight = PAGE_WIDTH_PT - MARGIN_PT - 12f
        val chartW = chartRight - chartLeft
        val chartH = chartBottom - chartTop

        val tMin = points.minOf { it.first }
        val tMax = points.maxOf { it.first }
        val spanT = max(1L, tMax - tMin)

        fun xFor(t: Long): Float = chartLeft + (t - tMin).toFloat() / spanT.toFloat() * chartW
        fun yFor(p: Float): Float {
            val pp = p.coerceIn(0f, 1f)
            return chartBottom - pp * chartH
        }

        canvas.drawRect(chartLeft, chartTop, chartRight, chartBottom, axisPaint)
        for (i in 0..4) {
            val gy = chartBottom - (i / 4f) * chartH
            canvas.drawLine(chartLeft, gy, chartRight, gy, gridPaint)
            canvas.drawText("${i * 25}%", chartLeft - 26f, gy + 3f, labelPaint)
        }

        val path = Path()
        points.forEachIndexed { i, pair ->
            val x = xFor(pair.first)
            val yy = yFor(pair.second)
            if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
        }
        canvas.drawPath(path, linePaint)

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val startLabel = fmt.format(Date(tMin))
        val endLabel = fmt.format(Date(tMax))
        val timeY = PAGE_HEIGHT_PT - MARGIN_PT - 14f
        canvas.drawText("Time →", chartLeft + chartW / 2f - 20f, timeY + 12f, labelPaint)
        canvas.drawText(startLabel, chartLeft, timeY, labelPaint)
        val endX = min(chartRight - labelPaint.measureText(endLabel), chartRight - 4f)
        canvas.drawText(endLabel, endX, timeY, labelPaint)
    }

    private fun wrapLines(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        val out = ArrayList<String>(text.count { it == '\n' } + 32)
        for (paragraph in text.split("\n")) {
            if (paragraph.isEmpty()) {
                out.add("")
                continue
            }
            var remaining = paragraph
            while (remaining.isNotEmpty()) {
                val count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                out.add(remaining.substring(0, count))
                remaining = remaining.substring(count)
            }
        }
        return out
    }
}
