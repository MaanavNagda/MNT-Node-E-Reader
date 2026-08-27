package com.example.optireader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.optireader.data.BookEntity
import com.example.optireader.reading.ReadingWpm
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun LibraryBookStatsOverlay(
    book: BookEntity,
    info: BookInfoUiData,
    totalReadingSeconds: Long,
    avgWpmUser: Float,
    avgWpmBook: Float,
    currentProgress01: Float,
    onDismiss: () -> Unit,
    onRatingChanged: (Float?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fmt = remember { DecimalFormat("0.0") }
    var snapshot by remember(book.id) { mutableStateOf(BookStatsStore.load(context, book.id)) }
    var overallNotes by remember(book.id) { mutableStateOf(snapshot.overallNotes) }
    var ratingInput by remember(book.id) { mutableStateOf(book.rating10?.let { fmt.format(it) } ?: "") }
    LaunchedEffect(book.id, currentProgress01, book.rating10) {
        snapshot = BookStatsStore.load(context, book.id)
        overallNotes = snapshot.overallNotes
        ratingInput = book.rating10?.let { fmt.format(it) } ?: ""
    }
    val parsedRating = ratingInput.toFloatOrNull()?.coerceIn(0f, 10f)
    val baselineWpm = when {
        avgWpmBook > 1f -> avgWpmBook
        avgWpmUser > 1f -> avgWpmUser
        else -> ReadingWpm.DEFAULT_WPM
    }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Stats", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Average speed so far: ${fmt.format(baselineWpm)} words/min")
                when (info.unitDisplay) {
                    BookInfoUnitDisplay.PAGES -> Text("Page count: ${info.totalUnits}")
                    BookInfoUnitDisplay.CHAPTERS -> Text("Page count: ${info.totalUnits}")
                }
                ReadThroughStatsBlock(
                    readSessions = snapshot.readSessions,
                    bookId = book.id,
                )
                Text("Time spent reading: ${statsFormatDuration(totalReadingSeconds)}")
                Text("")
                Text("Rating assigned", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = ratingInput,
                    onValueChange = { next ->
                        ratingInput = next
                        when {
                            next.isBlank() -> onRatingChanged(null)
                            else -> next.toFloatOrNull()?.coerceIn(0f, 10f)?.let(onRatingChanged)
                        }
                    },
                    label = { Text("Rating / 10.0") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Rating: ${parsedRating?.let { "${fmt.format(it)}/10.0" } ?: "NA"}")
                OutlinedTextField(
                    value = overallNotes,
                    onValueChange = { next ->
                        overallNotes = next
                        BookStatsStore.setOverallNotes(context, book.id, next)
                    },
                    label = { Text("Overall book notes") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Highlights", fontWeight = FontWeight.SemiBold)
                if (snapshot.highlights.isEmpty()) {
                    Text("No highlights yet.")
                } else {
                    snapshot.highlights.forEach { h ->
                        Text("- ${h.text}")
                        if (h.paragraph.isNotBlank()) {
                            Text(h.paragraph, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text("In-book notes", fontWeight = FontWeight.SemiBold)
                if (snapshot.inBookNotes.isEmpty()) {
                    Text("No notes yet.")
                } else {
                    snapshot.inBookNotes.forEach { n ->
                        if (n.anchorText.isNotBlank()) {
                            Text("On: ${n.anchorText}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("- ${n.note}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadThroughStatsBlock(
    readSessions: List<ReadSessionRecord>,
    bookId: Long,
) {
    var selectedReadIndex by remember(bookId) { mutableIntStateOf(-1) }
    var menuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(bookId, readSessions.size) {
        if (readSessions.isEmpty()) return@LaunchedEffect
        if (selectedReadIndex < 0 || selectedReadIndex >= readSessions.size) {
            selectedReadIndex = readSessions.lastIndex
        }
    }
    val effectiveIndex = if (readSessions.isEmpty()) {
        -1
    } else {
        when (selectedReadIndex) {
            in readSessions.indices -> selectedReadIndex
            else -> readSessions.lastIndex
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Reads: ${readSessions.size}",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (readSessions.isNotEmpty()) {
            Box {
                TextButton(onClick = { menuExpanded = true }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Read ${effectiveIndex + 1}")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    readSessions.forEachIndexed { index, _ ->
                        DropdownMenuItem(
                            text = { Text("Read ${index + 1}") },
                            onClick = {
                                selectedReadIndex = index
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        }
    }
    val selected = readSessions.getOrNull(effectiveIndex)
    if (selected == null) {
        Text("Not started", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Text(
        "Started: ${formatStatsDate(selected.dateStarted)}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        selected.dateFinished?.let { "Finished: ${formatStatsDate(it)}" } ?: "Finished: In progress",
        style = MaterialTheme.typography.bodyMedium,
    )
    ReadingProgressChart(
        points = selected.progressPoints,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(top = 8.dp),
    )
}

private fun formatStatsDate(isoDay: String): String =
    runCatching {
        LocalDate.parse(isoDay.trim()).format(
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()),
        )
    }.getOrElse { isoDay.trim() }

private fun statsFormatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun formatChartTimeTickLabel(atMillis: Long, chartStartMillis: Long, spanMs: Long): String {
    val zone = ZoneId.systemDefault()
    val elapsedMin = ((atMillis - chartStartMillis) / 60_000L).toInt().coerceAtLeast(0)
    return when {
        spanMs < 6 * 60 * 60 * 1000L -> {
            when {
                spanMs < 60 * 60 * 1000L -> "${elapsedMin}m"
                else -> {
                    val h = elapsedMin / 60
                    val m = elapsedMin % 60
                    when {
                        h == 0 -> "${m}m"
                        m == 0 -> "${h}h"
                        else -> "${h}h${m}m"
                    }
                }
            }
        }
        spanMs < 24 * 60 * 60 * 1000L ->
            Instant.ofEpochMilli(atMillis).atZone(zone)
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        spanMs < 14 * 24 * 60 * 60 * 1000L ->
            Instant.ofEpochMilli(atMillis).atZone(zone)
                .format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.getDefault()))
        spanMs < 56 * 24 * 60 * 60 * 1000L ->
            Instant.ofEpochMilli(atMillis).atZone(zone)
                .format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        else ->
            Instant.ofEpochMilli(atMillis).atZone(zone)
                .format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}

@Composable
private fun ReadingProgressChart(points: List<ProgressPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = Color.Gray.copy(alpha = 0.45f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val chartModel = remember(points) { buildReadingProgressChartModel(points) }
    val chartHeight = 130.dp
    Column(modifier = modifier) {
        Text("Reading progress", fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .width(40.dp)
                    .height(chartHeight),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text("100%", style = labelStyle, color = labelColor)
                Text("0%", style = labelStyle, color = labelColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight),
                ) {
                    val w = size.width
                    val h = size.height
                    drawLine(axisColor, Offset(0f, h), Offset(w, h), strokeWidth = 2f)
                    drawLine(axisColor, Offset(0f, 0f), Offset(0f, h), strokeWidth = 2f)
                    val series = chartModel.series
                    if (series.size < 2) return@Canvas
                    val t0 = chartModel.plotStartMillis
                    val span = chartModel.plotSpanMillis.coerceAtLeast(1L)
                    for (i in 0 until series.lastIndex) {
                        val (t1, p1) = series[i]
                        val (t2, p2) = series[i + 1]
                        val x1 = ((t1 - t0).toFloat() / span) * w
                        val y1 = h - (p1.coerceIn(0f, 1f) * h)
                        val x2 = ((t2 - t0).toFloat() / span) * w
                        val y2 = h - (p2.coerceIn(0f, 1f) * h)
                        drawLine(lineColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 4f)
                    }
                }
                if (chartModel.ticks.isNotEmpty()) {
                    ChartTimeAxisLabels(
                        ticks = chartModel.ticks,
                        plotStartMillis = chartModel.plotStartMillis,
                        plotSpanMillis = chartModel.plotSpanMillis,
                        chartStartForLabelsMillis = chartModel.chartStartForLabelsMillis,
                        spanRawMillis = chartModel.spanRawMillis,
                        labelStyle = labelStyle,
                        labelColor = labelColor,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartTimeAxisLabels(
    ticks: List<Long>,
    plotStartMillis: Long,
    plotSpanMillis: Long,
    chartStartForLabelsMillis: Long,
    spanRawMillis: Long,
    labelStyle: TextStyle,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            ticks.forEach { tick ->
                Text(
                    formatChartTimeTickLabel(tick, chartStartForLabelsMillis, spanRawMillis),
                    style = labelStyle,
                    color = labelColor,
                    maxLines = 1,
                )
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val placeables = measurables.map {
            it.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val h = placeables.maxOfOrNull { it.height } ?: 0
        val span = plotSpanMillis.coerceAtLeast(1L)
        layout(w, h) {
            ticks.forEachIndexed { i, tick ->
                val p = placeables.getOrNull(i) ?: return@forEachIndexed
                val frac = ((tick - plotStartMillis).toDouble() / span).toFloat().coerceIn(0f, 1f)
                val x = (frac * w - p.width / 2).roundToInt().coerceIn(0, (w - p.width).coerceAtLeast(0))
                p.place(x, 0)
            }
        }
    }
}

private data class ReadingProgressChartModel(
    val series: List<Pair<Long, Float>>,
    val plotStartMillis: Long,
    val plotSpanMillis: Long,
    val spanRawMillis: Long,
    val chartStartForLabelsMillis: Long,
    val ticks: List<Long>,
)

private fun buildReadingProgressChartModel(points: List<ProgressPoint>): ReadingProgressChartModel {
    val sorted = points
        .map { it.atMillis to it.progress01.coerceIn(0f, 1f) }
        .sortedBy { it.first }
    if (sorted.isEmpty()) {
        return ReadingProgressChartModel(emptyList(), 0L, 1L, 0L, 0L, emptyList())
    }
    var plotDataStart = sorted.first().first
    val plotDataEnd = sorted.last().first
    var spanRaw = (plotDataEnd - plotDataStart).coerceAtLeast(0L)
    if (spanRaw < 60_000L) {
        spanRaw = 60_000L
    }
    val padMs = (spanRaw * 0.08).toLong().coerceIn(30_000L, 8 * 60 * 60 * 1000L)
    val series = buildList {
        if (sorted.first().second > 0.002f) {
            val synth = (plotDataStart - padMs).coerceAtLeast(0L)
            add(synth to 0f)
        }
        sorted.forEach { add(it) }
    }
    val plotStartMillis = series.first().first
    val plotEndMillis = series.last().first
    val plotSpanMillis = (plotEndMillis - plotStartMillis).coerceAtLeast(60_000L)
    val spanForLabels = (plotDataEnd - plotDataStart).coerceAtLeast(60_000L)
    val tickCount = when {
        spanForLabels < 2 * 60 * 60 * 1000L -> 4
        spanForLabels < 24 * 60 * 60 * 1000L -> 5
        else -> 5
    }.coerceIn(2, 6)
    val denom = (tickCount - 1).coerceAtLeast(1)
    val ticks = (0 until tickCount).map { i ->
        plotStartMillis + (i * (plotEndMillis - plotStartMillis)) / denom
    }
    return ReadingProgressChartModel(
        series = series,
        plotStartMillis = plotStartMillis,
        plotSpanMillis = plotSpanMillis,
        spanRawMillis = spanForLabels,
        chartStartForLabelsMillis = plotDataStart,
        ticks = ticks,
    )
}

