package com.example.optireader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.optireader.R
import com.example.optireader.data.BookEntity
import com.example.optireader.reading.ReadingSpeedTracker
import com.example.optireader.reading.ReadingWpm
import com.example.optireader.epub.EpubChapterPageCount
import com.example.optireader.epub.EpubColumnLayoutCache
import com.example.optireader.epub.EpubOpfParser
import com.example.optireader.epub.EpubUnpacker
import com.example.optireader.epub.EpubReaderInsets
import com.example.optireader.epub.injectEpubPaginationCss
import com.example.optireader.epub.measureEpubChapterPageCounts
import com.example.optireader.epub.scrollEpubToPageIndex
import com.example.optireader.epub.epubPaginatedCss
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.roundToInt
import java.io.File
import java.text.DecimalFormat
import java.time.LocalDate
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    book: BookEntity,
    epubPageStyle: EpubPageStyle,
    onBack: () -> Unit,
    onReadingProgress: (Float) -> Unit = {},
    onRatingChanged: (Float?) -> Unit = {},
    /** Bumps when a finished EPUB is restarted from the beginning so the Readium fragment is recreated. */
    epubNavigatorResumeKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    var readerChromeVisible by remember { mutableStateOf(false) }
    ReaderSystemBarsVisibilityEffect()
    val readerTextColor = remember(epubPageStyle.foregroundCss) {
        runCatching {
            composeColorFromArgb(Color.parseColor(epubPageStyle.foregroundCss.trim()))
        }.getOrElse { ComposeColor.Black }
    }
    val toggleChrome = { readerChromeVisible = !readerChromeVisible }
    when (book.format.lowercase()) {
        "pdf" -> PdfReader(
            book = book,
            onBack = onBack,
            onReadingProgress = onReadingProgress,
            onRatingChanged = onRatingChanged,
            chromeVisible = readerChromeVisible,
            onToggleChrome = toggleChrome,
            readerTextColor = readerTextColor,
            epubPageStyle = epubPageStyle,
            modifier = modifier,
        )
        "epub" ->
            ReadiumEpubReaderHost(
                book = book,
                epubPageStyle = epubPageStyle,
                onBack = onBack,
                onReadingProgress = onReadingProgress,
                onRatingChanged = onRatingChanged,
                navigatorResumeKey = epubNavigatorResumeKey,
                modifier = modifier,
            )
        else -> UnsupportedReader(message = stringResource(R.string.reader_unsupported), onBack = onBack, modifier = modifier)
    }
}

@Composable
private fun ReaderSystemBarsVisibilityEffect() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.show(WindowInsetsCompat.Type.statusBars())
            c.hide(WindowInsetsCompat.Type.navigationBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            val w = view.context.findActivity()?.window ?: return@onDispose
            val c = WindowCompat.getInsetsController(w, view)
            c.show(WindowInsetsCompat.Type.navigationBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderChromeBottomDock(
    progress01: Float,
    progressLabel: String,
    onSeekFraction: (Float) -> Unit,
    /** True while the user drags the progress slider (excluded from reading-speed stats). */
    onSeekingChanged: (Boolean) -> Unit = {},
    onOpenThemeMenu: () -> Unit,
    onOpenBookMenu: () -> Unit,
    onShowBookInfo: () -> Unit,
    onShutdownApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val dockHeight = bottomInset + 112.dp
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        modifier = modifier
            .fillMaxWidth()
            .height(dockHeight),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = progress01.coerceIn(0f, 1f),
                onValueChange = {
                    onSeekingChanged(true)
                    onSeekFraction(it)
                },
                onValueChangeFinished = { onSeekingChanged(false) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenThemeMenu) {
                    Icon(Icons.Filled.Palette, contentDescription = "Theming menu")
                }
                IconButton(onClick = onOpenBookMenu) {
                    Icon(Icons.Filled.Settings, contentDescription = "Book settings")
                }
                IconButton(onClick = onShowBookInfo) {
                    Icon(Icons.Filled.Info, contentDescription = "Book info")
                }
                IconButton(onClick = onShutdownApp) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = "Shutdown app")
                }
            }
        }
    }
}

private fun readerDisplayTitle(book: BookEntity): String {
    val rawTitle = book.title.trim()
    val metadataTitle = when (book.format.lowercase()) {
        "epub" -> readEpubTitleFromLocal(File(book.localPath))
        "pdf" -> readPdfTitleFromLocal(File(book.localPath))
        else -> null
    }?.trim()
    val filenameFallback = File(book.localPath).nameWithoutExtension.trim()
    return when {
        !metadataTitle.isNullOrBlank() && !isGenericBookTitle(metadataTitle) -> metadataTitle
        rawTitle.isNotEmpty() && !isGenericBookTitle(rawTitle) -> rawTitle
        filenameFallback.isNotEmpty() -> filenameFallback
        else -> "Book"
    }
}

private fun readEpubTitleFromLocal(file: File): String? = runCatching {
    ZipFile(file).use { zip ->
        EpubOpfParser.readPackage(zip)?.title?.trim()?.takeIf { it.isNotEmpty() }
    }
}.getOrNull()

private fun readPdfTitleFromLocal(file: File): String? = runCatching {
    if (!file.exists()) return@runCatching null
    val text = file.inputStream().buffered().use { input ->
        input.readBytes().toString(Charsets.ISO_8859_1)
    }
    val candidates = Regex("""/Title\s*\(([^)]{1,300})\)""")
        .findAll(text)
        .mapNotNull { it.groupValues.getOrNull(1) }
        .map {
            it.replace("""\\\(""", "(")
                .replace("""\\\)""", ")")
                .replace("""\\n""", " ")
                .replace("""\\r""", " ")
                .replace("""\\t""", " ")
                .trim()
        }
        .filter { it.isNotBlank() }
        .toList()
    candidates
        .filterNot(::isGenericBookTitle)
        .maxByOrNull { it.length }
        ?: candidates.firstOrNull()
}.getOrNull()

private fun isGenericBookTitle(value: String): Boolean {
    val v = value.trim().lowercase()
    return v == "a novel" || v == "novel" || v == "untitled" || v == "unknown"
}

internal enum class BookInfoUnitDisplay {
    PAGES,
    CHAPTERS,
}

internal data class BookInfoUiData(
    val chapters: List<TocEntry>,
    val chapterPageIndexBySpine: Map<Int, Int>,
    val title: String,
    val author: String,
    val creator: String,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val totalUnits: Int,
    val currentUnit: Int,
    /** How [totalUnits] / [currentUnit] should be labeled (pages vs chapters). */
    val unitDisplay: BookInfoUnitDisplay = BookInfoUnitDisplay.PAGES,
    val wordsCount: Int,
    val charsCount: Int,
)

internal data class TocEntry(
    val title: String,
    val href: String,
)

private fun readPdfAuthorCreator(file: File): Pair<String?, String?> = runCatching {
    val text = file.inputStream().buffered().use { it.readBytes().toString(Charsets.ISO_8859_1) }
    val author = Regex("""/Author\s*\(([^)]{1,300})\)""").find(text)?.groupValues?.getOrNull(1)?.trim()
    val creator = Regex("""/Creator\s*\(([^)]{1,300})\)""").find(text)?.groupValues?.getOrNull(1)?.trim()
    author to creator
}.getOrElse { null to null }

private fun estimateWordAndCharCountForPdf(file: File): Pair<Int, Int> = runCatching {
    val text = file.inputStream().buffered().use { it.readBytes().toString(Charsets.ISO_8859_1) }
    val chunks = Regex("""\(([^\)]{2,200})\)""").findAll(text).map { it.groupValues[1] }.toList()
    val plain = chunks.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    val words = if (plain.isBlank()) 0 else plain.split(' ').size
    words to plain.length
}.getOrElse { 0 to 0 }

@Composable
internal fun BookInfoPageOverlay(
    book: BookEntity,
    info: BookInfoUiData,
    coverPath: String?,
    totalReadingSeconds: Long,
    sessionReadingSeconds: Long,
    /** If >= 0, used instead of [sessionReadingSeconds] for instant WPM (excludes scrubbing / idle). */
    validReadingSecondsForWpm: Long = -1L,
    historyDays: Int,
    avgWpmUser: Float,
    avgWpmBook: Float,
    currentProgress01: Float,
    onOpenChapterLink: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    onRatingChanged: (Float?) -> Unit = {},
    /** When non-null, a third pager page lists bookmarks (reader only). */
    bookmarks: List<BookmarkEntry> = emptyList(),
    onBookmarkSelected: ((BookmarkEntry) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val fmt = remember { DecimalFormat("0.0") }
    var ratingInput by remember(book.id) { mutableStateOf("") }
    LaunchedEffect(book.id, book.rating10) {
        ratingInput = book.rating10?.let { fmt.format(it) } ?: ""
    }
    val parsedRating = ratingInput.toFloatOrNull()?.coerceIn(0f, 10f)
    val rating10 = parsedRating ?: 0f
    val wordsRead = (info.wordsCount * currentProgress01).toInt()
    val secForRate = if (validReadingSecondsForWpm >= 0L) validReadingSecondsForWpm else sessionReadingSeconds
    val minutesSpent = (secForRate / 60f).coerceAtLeast(0.1f)
    val currentWpm = if (wordsRead > 0) wordsRead / minutesSpent else 0f
    val baselineWpm = when {
        avgWpmBook > 1f -> avgWpmBook
        avgWpmUser > 1f -> avgWpmUser
        else -> ReadingWpm.DEFAULT_WPM
    }
    val effectiveWpm = if (currentWpm > 1f) currentWpm else baselineWpm
    val remainingWords = (info.wordsCount - wordsRead).coerceAtLeast(0)
    val etaMinutes = if (effectiveWpm > 1f) remainingWords / effectiveWpm else 0f
    val infoPageCount = if (onBookmarkSelected != null) 3 else 2
    val pager = rememberPagerState(pageCount = { infoPageCount })
    val bookmarkList = remember(bookmarks) { sortedBookmarksForDisplay(bookmarks) }

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
                Text(
                    when (pager.currentPage) {
                        0 -> "Book info"
                        1 -> "Chapters"
                        else -> "Bookmarks"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                AsyncImage(
                                    model = coverPath,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(140.dp, 200.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedTextField(
                                        value = ratingInput,
                                        onValueChange = { next ->
                                            ratingInput = next
                                            when {
                                                next.isBlank() -> onRatingChanged(null)
                                                else -> {
                                                    val p = next.toFloatOrNull()?.coerceIn(0f, 10f)
                                                    if (p != null) onRatingChanged(p)
                                                }
                                            }
                                        },
                                        label = { Text("Rating / 10.0") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = "Rating: ${parsedRating?.let { "${fmt.format(it)}/10.0" } ?: "NA"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    StarRating10(
                                        rating10 = rating10,
                                        onRateSelected = { selected ->
                                            val v = selected.toFloat().coerceIn(0f, 10f)
                                            ratingInput = "${selected}.0"
                                            onRatingChanged(v)
                                        },
                                    )
                                }
                            }
                            Text("Title: ${info.title}")
                            Text("Author: ${info.author}")
                            Text("")
                            Text("File name: ${info.fileName}")
                            Text("File location: ${info.filePath}")
                            Text("File size: ${humanReadableSize(info.fileSizeBytes)}")
                            when (info.unitDisplay) {
                                BookInfoUnitDisplay.PAGES -> {
                                    Text("Total pages: ${info.totalUnits}")
                                    Text("Current page: ${info.currentUnit}")
                                }
                                BookInfoUnitDisplay.CHAPTERS -> {
                                    Text("Chapters: ${info.totalUnits}")
                                    Text("Current chapter: ${info.currentUnit}")
                                }
                            }
                            Text("Creator: ${info.creator}")
                            Text("")
                            Text("Time spent reading: ${formatDuration(totalReadingSeconds)}")
                            Text("This session: ${formatDuration(sessionReadingSeconds)}")
                            Text("Read speed: ${fmt.format(if (currentWpm > 0f) currentWpm else baselineWpm)} words/min")
                            Text("Reading history (days): $historyDays")
                            Text("")
                            Text("Characters in book: ${info.charsCount}")
                            Text("Words in book: ${info.wordsCount}")
                            Text("")
                            Text("Estimated time left: ${fmt.format(etaMinutes)} minutes")
                        }
                    }
                    1 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (info.chapters.isEmpty()) {
                                Text("No chapter list found in table of contents.")
                            } else {
                                info.chapters.forEachIndexed { idx, chapter ->
                                    Text(
                                        text = "${idx + 1}. ${chapter.title}",
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable(enabled = onOpenChapterLink != null) {
                                            onOpenChapterLink?.invoke(chapter.href)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (bookmarkList.isEmpty()) {
                                Text("No bookmarks yet. Tap the top-right corner while reading to add one.")
                            } else {
                                bookmarkList.forEachIndexed { idx, bm ->
                                    val pageLabel = when (bm.format) {
                                        "pdf" -> "Page ${(bm.pdfPageIndex ?: 0) + 1}"
                                        else -> "Position ${(bm.progress01 * 100f).toInt()}%"
                                    }
                                    Text(
                                        text = "${idx + 1}. $pageLabel\n${bm.previewText}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.clickable {
                                            onBookmarkSelected?.invoke(bm)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun StarRating10(
    rating10: Float,
    onRateSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { col ->
                    val index = row * 5 + col
                    val fill = (rating10 - index).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onRateSelected(index + 1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.StarBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fill)
                                .clipToBounds(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = ComposeColor(0xFFFFC107),
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun humanReadableSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024f
    if (kb < 1024f) return "${DecimalFormat("0.0").format(kb)} KB"
    val mb = kb / 1024f
    if (mb < 1024f) return "${DecimalFormat("0.0").format(mb)} MB"
    val gb = mb / 1024f
    return "${DecimalFormat("0.0").format(gb)} GB"
}

@Composable
private fun ReaderCenterTapZone(
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
    /** When false, the center tap target is invisible (PDF); EPUB keeps the cue by default. */
    showVisualCue: Boolean = true,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val tapW = maxWidth * 0.42f
        val tapH = maxHeight * 0.42f
        val tapSlopPx = with(density) { 12.dp.toPx() }
        var capturingTouch by remember { mutableStateOf(false) }
        var downX by remember { mutableStateOf(0f) }
        var downY by remember { mutableStateOf(0f) }
        var moved by remember { mutableStateOf(false) }
        val cueModifier = if (showVisualCue) {
            Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(10.dp),
                )
        } else {
            Modifier
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .width(tapW)
                .height(tapH)
                .then(cueModifier)
                .pointerInteropFilter { ev ->
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            capturingTouch = true
                            moved = false
                            downX = ev.x
                            downY = ev.y
                            // Touches starting inside this box are captured as touch interactions.
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!capturingTouch) return@pointerInteropFilter false
                            if (!moved) {
                                val dx = ev.x - downX
                                val dy = ev.y - downY
                                moved = (dx * dx + dy * dy) > (tapSlopPx * tapSlopPx)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!capturingTouch) return@pointerInteropFilter false
                            if (!moved) onToggleChrome()
                            capturingTouch = false
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            val wasCapturing = capturingTouch
                            capturingTouch = false
                            wasCapturing
                        }
                        else -> capturingTouch
                    }
                },
        )
    }
}

/** Top-right tap adds a bookmark; optional marker when this location is bookmarked. */
@Composable
internal fun ReaderBookmarkCornerOverlay(
    bookmarkColorArgb: Int,
    showMarker: Boolean,
    onCornerTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().zIndex(12f)) {
        val density = LocalDensity.current
        val tapW = maxWidth * 0.26f
        val tapH = maxHeight * 0.18f
        val tapSlopPx = with(density) { 12.dp.toPx() }
        var capturingTouch by remember { mutableStateOf(false) }
        var downX by remember { mutableStateOf(0f) }
        var downY by remember { mutableStateOf(0f) }
        var moved by remember { mutableStateOf(false) }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .width(tapW)
                .height(tapH)
                .zIndex(8f)
                .pointerInteropFilter { ev ->
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            capturingTouch = true
                            moved = false
                            downX = ev.x
                            downY = ev.y
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (!capturingTouch) return@pointerInteropFilter false
                            if (!moved) {
                                val dx = ev.x - downX
                                val dy = ev.y - downY
                                moved = (dx * dx + dy * dy) > (tapSlopPx * tapSlopPx)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!capturingTouch) return@pointerInteropFilter false
                            if (!moved) onCornerTap()
                            capturingTouch = false
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            val wasCapturing = capturingTouch
                            capturingTouch = false
                            wasCapturing
                        }
                        else -> capturingTouch
                    }
                },
        ) {
            if (showMarker) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = "Bookmark",
                    tint = composeColorFromArgb(bookmarkColorArgb),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(30.dp),
                )
            }
        }
    }
}

/** Full-screen frame overlay (does not intercept touches). Top/bottom 0.8 cm, sides 0.6 cm — same fill/stroke style as [ReaderCenterTapZone]. */
@Composable
private fun EpubReaderBorderOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Same physical inset as WebView padding (band mm + half stroke) so the frame matches the page.
    val topBandPx = (
        EpubReaderInsets.borderTopBottomPx(context) + EpubReaderInsets.borderStrokeHalfPx(context)
    ).toFloat()
    val sideBandPx = (
        EpubReaderInsets.borderSidePx(context) + EpubReaderInsets.borderStrokeHalfPx(context)
    ).toFloat()
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val strokeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    val strokeW = EpubReaderInsets.borderStrokeWidthPx(context).toFloat()
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInteropFilter { false }
            .drawBehind {
                val w = size.width
                val h = size.height
                fun bandStroke(topLeft: Offset, s: Size) {
                    drawRect(
                        color = strokeColor,
                        topLeft = topLeft,
                        size = s,
                        style = Stroke(width = strokeW),
                    )
                }
                // Top
                drawRect(fill, topLeft = Offset(0f, 0f), size = Size(w, topBandPx))
                bandStroke(Offset(0f, 0f), Size(w, topBandPx))
                // Bottom
                drawRect(fill, topLeft = Offset(0f, h - topBandPx), size = Size(w, topBandPx))
                bandStroke(Offset(0f, h - topBandPx), Size(w, topBandPx))
                // Left
                drawRect(fill, topLeft = Offset(0f, 0f), size = Size(sideBandPx, h))
                bandStroke(Offset(0f, 0f), Size(sideBandPx, h))
                // Right
                drawRect(fill, topLeft = Offset(w - sideBandPx, 0f), size = Size(sideBandPx, h))
                bandStroke(Offset(w - sideBandPx, 0f), Size(sideBandPx, h))
            },
    )
}

@Composable
private fun ReaderDismissTapLayer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(onDismiss) {
                detectTapGestures(onTap = { onDismiss() })
            },
    )
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

data class EpubPageStyle(
    val backgroundCss: String,
    val foregroundCss: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnsupportedReader(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(message) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Text(
            text = message,
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfReader(
    book: BookEntity,
    onBack: () -> Unit,
    onReadingProgress: (Float) -> Unit,
    onRatingChanged: (Float?) -> Unit,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    readerTextColor: ComposeColor,
    epubPageStyle: EpubPageStyle,
    modifier: Modifier = Modifier,
) {
    val file = remember(book.localPath) { File(book.localPath) }
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var showBookInfo by remember { mutableStateOf(false) }
    var showBookSettings by remember(book.id) { mutableStateOf(false) }
    var totalReadingSeconds by remember(book.id) { mutableStateOf(0L) }
    var sessionReadingSeconds by remember(book.id) { mutableStateOf(0L) }
    var avgWpmUser by remember { mutableStateOf(0f) }
    var avgWpmBook by remember { mutableStateOf(0f) }
    var historyDays by remember { mutableIntStateOf(1) }
    var infoData by remember {
        mutableStateOf(
            BookInfoUiData(
                chapters = emptyList(),
                chapterPageIndexBySpine = emptyMap(),
                title = readerDisplayTitle(book),
                author = "Unknown",
                creator = "Unknown",
                fileName = file.name,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                totalUnits = 0,
                currentUnit = 1,
                wordsCount = 0,
                charsCount = 0,
            ),
        )
    }
    val pfd = remember(file.absolutePath) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull()
    }
    val renderer = remember(pfd) {
        pfd?.let { PdfRenderer(it) }
    }

    DisposableEffect(renderer, pfd) {
        onDispose {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    LaunchedEffect(renderer) {
        pageCount = renderer?.pageCount ?: 0
    }

    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val readerPrefs = remember { context.getSharedPreferences("reader_options", Context.MODE_PRIVATE) }
    var bookmarks by remember(book.id) { mutableStateOf<List<BookmarkEntry>>(emptyList()) }
    var bookmarkRefreshKey by remember(book.id) { mutableIntStateOf(0) }
    var didApplyInitialPdfPage by remember(book.id) { mutableStateOf(false) }
    var bookmarkColorArgb by remember(book.id) {
        mutableIntStateOf(loadBookmarkIconColorArgb(readerPrefs))
    }
    LaunchedEffect(book.id, bookmarkRefreshKey) {
        bookmarks = BookmarkStore.load(context, book.id)
    }
    LaunchedEffect(book.id, pageCount) {
        if (pageCount <= 0) return@LaunchedEffect
        if (didApplyInitialPdfPage) return@LaunchedEffect
        val latest = BookmarkStore.getLatest(context, book.id)
        val targetPage = when {
            latest != null && latest.format == "pdf" && latest.pdfPageIndex != null ->
                latest.pdfPageIndex.coerceIn(0, pageCount - 1)
            else ->
                ((book.readProgress01 * (pageCount - 1).coerceAtLeast(1)).roundToInt()).coerceIn(0, pageCount - 1)
        }
        scope.launch { pagerState.scrollToPage(targetPage) }
        didApplyInitialPdfPage = true
    }
    val pdfFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1),
    )

    LaunchedEffect(pagerState, pageCount) {
        snapshotFlow {
            if (pageCount <= 0) null
            else (pagerState.currentPage + 1).toFloat() / pageCount
        }
            .distinctUntilChanged()
            .collect { v -> v?.let(onReadingProgress) }
    }
    val pdfProgress = if (pageCount > 0) {
        (pagerState.currentPage + 1).toFloat() / pageCount.toFloat()
    } else {
        0f
    }
    val wpmTracker = remember(book.id, pageCount, infoData.wordsCount) {
        val prefs = context.getSharedPreferences(ReadingWpm.PREFS_NAME, Context.MODE_PRIVATE)
        ReadingWpm.ensureBookWpmSeeded(context, book.id)
        val seed = prefs.getFloat("${ReadingWpm.PREFIX_AVG_WPM_BOOK}${book.id}", ReadingWpm.DEFAULT_WPM)
        ReadingSpeedTracker(
            totalWords = infoData.wordsCount.coerceAtLeast(1),
            totalUnits = pageCount.coerceAtLeast(1),
            seedWpm = seed,
        )
    }
    LaunchedEffect(pagerState.currentPage, pageCount) {
        if (pageCount > 0) wpmTracker.onUnitIndexChanged(pagerState.currentPage)
    }
    LaunchedEffect(book.id) {
        totalReadingSeconds = ReaderReadingTime.loadTotalSeconds(context, book.id)
        sessionReadingSeconds = 0L
    }

    LaunchedEffect(book.id) {
        val prefs = context.getSharedPreferences(ReadingWpm.PREFS_NAME, Context.MODE_PRIVATE)
        val bookKey = "book_days_${book.id}"
        val today = LocalDate.now().toString()
        val daySet = prefs.getStringSet(bookKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        daySet.add(today)
        prefs.edit().putStringSet(bookKey, daySet).apply()
        historyDays = daySet.size
        ReadingWpm.ensureBookWpmSeeded(context, book.id)
        avgWpmUser = prefs.getFloat(ReadingWpm.KEY_AVG_WPM_USER, 0f)
        avgWpmBook = prefs.getFloat("${ReadingWpm.PREFIX_AVG_WPM_BOOK}${book.id}", 0f)
        val title = readerDisplayTitle(book)
        val (author, creator) = readPdfAuthorCreator(file)
        val (words, chars) = withContext(Dispatchers.IO) { estimateWordAndCharCountForPdf(file) }
        infoData = infoData.copy(
            title = title,
            author = author ?: "Unknown",
            creator = creator ?: "Unknown",
            fileName = file.name,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            totalUnits = pageCount,
            wordsCount = words,
            charsCount = chars,
        )
    }
    LaunchedEffect(pageCount) {
        infoData = infoData.copy(totalUnits = pageCount, currentUnit = (pagerState.currentPage + 1))
    }
    LaunchedEffect(pagerState.currentPage) {
        infoData = infoData.copy(currentUnit = pagerState.currentPage + 1)
    }
    LaunchedEffect(book.id) {
        while (true) {
            delay(1000)
            sessionReadingSeconds += 1
            totalReadingSeconds += 1
            ReaderReadingTime.saveTotalSeconds(context, book.id, totalReadingSeconds)
            wpmTracker.onTickSecond()
            val prefs = context.getSharedPreferences(ReadingWpm.PREFS_NAME, Context.MODE_PRIVATE)
            val wordsRead = (infoData.wordsCount * pdfProgress).toInt()
            val wpm = wpmTracker.sessionWpm(wordsRead)
            if (wpm > 1f) {
                ReadingWpm.persistSessionSample(prefs, book.id, wpm)
                avgWpmUser = prefs.getFloat(ReadingWpm.KEY_AVG_WPM_USER, 0f)
                avgWpmBook = prefs.getFloat("${ReadingWpm.PREFIX_AVG_WPM_BOOK}${book.id}", 0f)
            }
        }
    }
    val pdfPositionLabel = "Page ${pagerState.currentPage + 1} / $pageCount"
    var globalBookThemeBaselineKey by remember { mutableIntStateOf(0) }
    val pdfDefaultBackdropArgb = remember(globalBookThemeBaselineKey, epubPageStyle) {
        resolveEpubThemeBaseline(readerPrefs, epubPageStyle).pageArgb
    }
    var pdfPageBackdropArgb by remember(book.id) {
        mutableStateOf(
            readerPrefs.getInt(
                "pdf_page_bg_${book.id}",
                resolveEpubThemeBaseline(readerPrefs, epubPageStyle).pageArgb,
            ),
        )
    }
    LaunchedEffect(book.id, pdfDefaultBackdropArgb) {
        if (!readerPrefs.contains("pdf_page_bg_${book.id}")) {
            pdfPageBackdropArgb = pdfDefaultBackdropArgb
        }
    }
    var showPdfTheme by remember(book.id) { mutableStateOf(false) }
    var showPdfColorPicker by remember(book.id) { mutableStateOf(false) }
    var readerControls by remember(book.id) { mutableStateOf(loadBookReaderControlsSettings(readerPrefs, book.id)) }
    var volumeOverlayPct by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(readerPrefs, book.id) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            readerControls = loadBookReaderControlsSettings(readerPrefs, book.id)
            if (key == KEY_GLOBAL_BOOK_THEME_MODE || key == KEY_GLOBAL_BOOK_THEME_PROFILE_NAME) {
                globalBookThemeBaselineKey++
            }
            if (key == KEY_BOOKMARK_ICON_COLOR_ARGB || key == null) {
                bookmarkColorArgb = loadBookmarkIconColorArgb(readerPrefs)
            }
        }
        readerPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { readerPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    ReaderStatusBarAppearanceEffect(
        contentColor = readerTextColor,
        windowBarBackgroundColor = composeColorFromArgb(pdfPageBackdropArgb),
    )

    if (renderer == null || pageCount <= 0) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(composeColorFromArgb(pdfPageBackdropArgb)),
        ) {
            Text(
                text = stringResource(R.string.reader_unsupported),
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    ReaderVolumeKeysInterceptor(
        controls = readerControls,
        onForward = { if (pageCount > 0) scope.launch { pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pageCount - 1)) } },
        onBackward = { if (pageCount > 0) scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
    ) { keyMod ->
    Box(
        modifier = modifier
            .then(keyMod)
            .fillMaxSize()
            .background(composeColorFromArgb(pdfPageBackdropArgb)),
    ) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .readerEdgeSlideControls(
                        context = context,
                        controls = readerControls,
                        onVolumeChangedPercent = { volumeOverlayPct = it },
                    ),
                beyondViewportPageCount = 2,
                flingBehavior = pdfFlingBehavior,
                userScrollEnabled = true,
            ) { page ->
                PdfPageBitmap(
                    renderer = renderer,
                    pageIndex = page,
                    pageBackdropArgb = pdfPageBackdropArgb,
                )
            }
            if (chromeVisible) {
                ReaderDismissTapLayer(onDismiss = onToggleChrome)
            }
            ReaderCenterTapZone(onToggleChrome = onToggleChrome, showVisualCue = false)
            if (!showBookInfo && !showBookSettings && !showPdfTheme && !showPdfColorPicker) {
                ReaderBookmarkCornerOverlay(
                    bookmarkColorArgb = bookmarkColorArgb,
                    showMarker = bookmarks.any { bookmarkMatchesPdfPage(it, pagerState.currentPage) },
                    onCornerTap = {
                        if (pageCount <= 0) return@ReaderBookmarkCornerOverlay
                        BookmarkStore.addPdf(
                            context,
                            book.id,
                            pagerState.currentPage,
                            pdfProgress,
                            "Page ${pagerState.currentPage + 1}",
                        )
                        bookmarkRefreshKey++
                    },
                )
            }
            if (volumeOverlayPct != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Volume ${volumeOverlayPct}%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (chromeVisible) {
            ReaderChromeBottomDock(
                progress01 = pdfProgress,
                progressLabel = pdfPositionLabel,
                onSeekFraction = { fraction ->
                    if (pageCount <= 0) return@ReaderChromeBottomDock
                    val target = ((fraction.coerceIn(0f, 1f) * pageCount).toInt())
                        .coerceIn(1, pageCount) - 1
                    scope.launch { pagerState.scrollToPage(target) }
                },
                onSeekingChanged = { wpmTracker.setScrubbing(it) },
                onOpenThemeMenu = { showPdfTheme = true },
                onOpenBookMenu = { showBookSettings = true },
                onShowBookInfo = { showBookInfo = true },
                onShutdownApp = onBack,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (
            !showBookInfo &&
            !showBookSettings &&
            !showPdfTheme &&
            !showPdfColorPicker &&
            pageCount > 0
        ) {
            val currentPageInChapter = (pagerState.currentPage + 1).coerceAtLeast(1)
            val chapterTotalPages = pageCount.coerceAtLeast(1)
            val remainingUnitsChapter = (chapterTotalPages - currentPageInChapter).coerceAtLeast(0)
            val etaInfo = computeReaderEtaInfo(
                wordsCount = infoData.wordsCount,
                progress01Book = pdfProgress,
                totalUnitsBook = pageCount.coerceAtLeast(1),
                validReadingSecondsForWpm = wpmTracker.validReadSeconds(),
                sessionReadingSeconds = sessionReadingSeconds,
                avgWpmUser = avgWpmUser,
                avgWpmBook = avgWpmBook,
                remainingUnitsChapter = remainingUnitsChapter,
            )
            ReaderMinimalStatusBar(
                chapterTitle = "Page $currentPageInChapter / $chapterTotalPages",
                chapterPageText = "",
                readProgress01 = pdfProgress,
                etaInfo = etaInfo,
                contentColor = readerTextColor,
                backgroundColor = composeColorFromArgb(pdfPageBackdropArgb),
                controls = readerControls,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (showBookInfo) {
            BookInfoPageOverlay(
                book = book,
                info = infoData,
                coverPath = book.coverPath,
                totalReadingSeconds = totalReadingSeconds,
                sessionReadingSeconds = sessionReadingSeconds,
                validReadingSecondsForWpm = wpmTracker.validReadSeconds(),
                historyDays = historyDays,
                avgWpmUser = avgWpmUser,
                avgWpmBook = avgWpmBook,
                currentProgress01 = pdfProgress,
                onOpenChapterLink = null,
                onDismiss = { showBookInfo = false },
                onRatingChanged = onRatingChanged,
                bookmarks = bookmarks,
                onBookmarkSelected = { bm ->
                    showBookInfo = false
                    if (bm.format == "pdf" && bm.pdfPageIndex != null && pageCount > 0) {
                        scope.launch {
                            pagerState.scrollToPage(bm.pdfPageIndex.coerceIn(0, pageCount - 1))
                        }
                    }
                },
            )
        }
        if (showBookSettings) {
            AlertDialog(
                onDismissRequest = { showBookSettings = false },
                title = { Text("Book settings") },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        ReaderControlsSettingsSection(
                            settings = readerControls,
                            onSettingsChange = { next ->
                                readerControls = next
                                saveBookReaderControlsSettings(readerPrefs, book.id, next)
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBookSettings = false }) { Text("Done") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            clearBookReaderControlsSettings(readerPrefs, book.id)
                            readerControls = loadBookReaderControlsSettings(readerPrefs, book.id)
                        },
                    ) { Text(stringResource(R.string.reader_controls_use_global_defaults)) }
                },
            )
        }

        if (showPdfTheme) {
            val pdfThemeScroll = rememberScrollState()
            AlertDialog(
                onDismissRequest = { showPdfTheme = false },
                title = { Text("Theme") },
                text = {
                    Column(Modifier.verticalScroll(pdfThemeScroll)) {
                        ThemeColorSwatch(
                            colorArgb = pdfPageBackdropArgb,
                            label = "Background behind pages",
                            onClick = { showPdfColorPicker = true },
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        ReaderGlobalThemePreferences(readerOptionsPrefs = readerPrefs)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPdfTheme = false }) { Text("Done") }
                },
            )
        }
        if (showPdfColorPicker) {
            HsvColorPickerDialog(
                title = "Page background",
                initialArgb = pdfPageBackdropArgb,
                onDismiss = { showPdfColorPicker = false },
                onConfirm = { argb ->
                    pdfPageBackdropArgb = argb
                    readerPrefs.edit().putInt("pdf_page_bg_${book.id}", argb).apply()
                    showPdfColorPicker = false
                },
            )
        }
    }
    LaunchedEffect(volumeOverlayPct) {
        if (volumeOverlayPct != null) {
            delay(900)
            volumeOverlayPct = null
        }
    }
    }
}

@Composable
private fun PdfPageBitmap(
    renderer: PdfRenderer,
    pageIndex: Int,
    pageBackdropArgb: Int,
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(renderer, pageIndex) {
        bitmap = withContext(Dispatchers.Default) {
            renderer.openPage(pageIndex).use { page ->
                val w = page.width.coerceAtMost(2048)
                val h = (page.height * (w.toFloat() / page.width)).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }
    val b = bitmap
    if (b != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(composeColorFromArgb(pageBackdropArgb)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UnusedPrivateMember")
private fun EpubReader(
    book: BookEntity,
    epubPageStyle: EpubPageStyle,
    onBack: () -> Unit,
    onReadingProgress: (Float) -> Unit,
    onRatingChanged: (Float?) -> Unit = {},
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readerTextColor = remember(epubPageStyle.foregroundCss) {
        runCatching {
            composeColorFromArgb(Color.parseColor(epubPageStyle.foregroundCss.trim()))
        }.getOrElse { ComposeColor.Black }
    }
    val readerPageBg = remember(epubPageStyle.backgroundCss) {
        runCatching {
            composeColorFromArgb(Color.parseColor(epubPageStyle.backgroundCss.trim()))
        }.getOrElse { ComposeColor.White }
    }
    ReaderStatusBarAppearanceEffect(
        contentColor = readerTextColor,
        windowBarBackgroundColor = readerPageBg,
    )
    val context = LocalContext.current
    val density = LocalDensity.current
    val epubFile = remember(book.localPath) { File(book.localPath) }
    val unpackDir = remember(book.id) { File(context.cacheDir, "epub_${book.id}") }

    var spineUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var spineIndex by remember { mutableIntStateOf(0) }
    var pageInChapter by remember { mutableIntStateOf(0) }
    var chapterPageCounts by remember { mutableStateOf<List<EpubChapterPageCount>>(emptyList()) }
    var layoutLoading by remember { mutableStateOf(true) }
    var layoutProgress by remember { mutableStateOf(0 to 0) }
    var didInitPosition by remember(book.id) { mutableStateOf(false) }
    var swipeLocked by remember { mutableStateOf(false) }

    var showBookInfo by remember { mutableStateOf(false) }
    var totalReadingSeconds by remember(book.id) { mutableStateOf(0L) }
    var sessionReadingSeconds by remember(book.id) { mutableStateOf(0L) }
    var avgWpmUser by remember { mutableStateOf(0f) }
    var avgWpmBook by remember { mutableStateOf(0f) }
    var historyDays by remember { mutableIntStateOf(1) }
    var infoData by remember {
        mutableStateOf(
            BookInfoUiData(
                chapters = emptyList(),
                chapterPageIndexBySpine = emptyMap(),
                title = readerDisplayTitle(book),
                author = "Unknown",
                creator = "Unknown",
                fileName = epubFile.name,
                filePath = epubFile.absolutePath,
                fileSizeBytes = epubFile.length(),
                totalUnits = 0,
                currentUnit = 1,
                wordsCount = 0,
                charsCount = 0,
            ),
        )
    }

    /**
     * Band thickness (mm) + half the outline stroke px: stroke is centered on the band edge, so
     * inner edge of the border line is inset [EpubReaderInsets.borderStrokeHalfPx] from the band rect.
     */
    val borderFrameTopPx = remember(context) {
        EpubReaderInsets.borderTopBottomPx(context) + EpubReaderInsets.borderStrokeHalfPx(context)
    }
    val borderFrameSidePx = remember(context) {
        EpubReaderInsets.borderSidePx(context) + EpubReaderInsets.borderStrokeHalfPx(context)
    }
    var measuredViewportCss by remember(book.id) { mutableStateOf<Pair<Int, Int>?>(null) }

    val webView = remember(book.id) {
        WebView(context).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = false
            settings.textZoom = 100
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            setBackgroundColor(Color.parseColor(epubPageStyle.backgroundCss))
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    fun totalBookPages(): Int = chapterPageCounts.sumOf { it.pageCount }.coerceAtLeast(1)

    fun globalPageIndex(): Int {
        var g = 0
        for (i in 0 until spineIndex) {
            g += chapterPageCounts.getOrNull(i)?.pageCount ?: 0
        }
        return g + pageInChapter
    }

    LaunchedEffect(book.id, book.localPath) {
        withContext(Dispatchers.IO) {
            EpubUnpacker.ensureUnpacked(epubFile, unpackDir)
            val urls = ZipFile(epubFile).use { zip ->
                val pkg = EpubOpfParser.readPackage(zip) ?: return@use emptyList()
                pkg.spinePathsInZip().map { path -> File(unpackDir, path).toURI().toString() }
            }
            spineUrls = urls
            spineIndex = 0
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // WebView CSS px ≈ dp: physical px / density. Prefer measured pane size so pagination matches
        // the actual WebView (avoids EPUB viewport / layout mismatch and "huge page" scaling).
        val densityScale = density.density.coerceAtLeast(0.01f)
        val topBorderDp = (borderFrameTopPx / densityScale).dp
        val sideBorderDp = (borderFrameSidePx / densityScale).dp
        val innerMaxW = (maxWidth - sideBorderDp * 2).coerceAtLeast(1.dp)
        val innerMaxH = (maxHeight - topBorderDp * 2).coerceAtLeast(1.dp)
        // Floor: a rounded-up CSS viewport is one common cause of ~1px bleed past the clip rect.
        val constraintW = with(density) { floor(innerMaxW.toPx() / densityScale).toInt().coerceAtLeast(1) }
        val constraintH = with(density) { floor(innerMaxH.toPx() / densityScale).toInt().coerceAtLeast(1) }
        val viewportWidthCssPx = measuredViewportCss?.first ?: constraintW
        val viewportHeightCssPx = measuredViewportCss?.second ?: constraintH
        // Single column width = full inner viewport (CSS px). No extra mm trim — avoids rounding drift vs WebView.
        val padLeftCss = 0
        val padRightCss = 0
        val padTopCss = 0
        val padBottomCss = 0
        val contentWcss = viewportWidthCssPx.coerceAtLeast(80)

        val css = remember(
            viewportWidthCssPx,
            viewportHeightCssPx,
            epubPageStyle,
            padTopCss,
            padLeftCss,
            padRightCss,
            padBottomCss,
        ) {
            epubPaginatedCss(
                viewportWidthCssPx,
                viewportHeightCssPx,
                padTopCss,
                padLeftCss,
                padRightCss,
                padBottomCss,
                epubPageStyle.backgroundCss,
                epubPageStyle.foregroundCss,
            )
        }

        val latestCss by rememberUpdatedState(css)
        val latestContentW by rememberUpdatedState(contentWcss)
        val latestVw by rememberUpdatedState(viewportWidthCssPx)
        val latestVh by rememberUpdatedState(viewportHeightCssPx)
        val latestPt by rememberUpdatedState(padTopCss)
        val latestPr by rememberUpdatedState(padRightCss)
        val latestPb by rememberUpdatedState(padBottomCss)
        val latestPl by rememberUpdatedState(padLeftCss)

        fun applyPaginationAndScroll(page: Int) {
            injectEpubPaginationCss(
                webView,
                latestCss,
                latestContentW,
                latestVw,
                latestVh,
                latestPt,
                latestPr,
                latestPb,
                latestPl,
            )
            webView.postDelayed({
                scrollEpubToPageIndex(webView, page, latestContentW)
            }, 80)
        }

        fun installReadingClient() {
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view ?: return
                    applyPaginationAndScroll(pageInChapter)
                    view.postDelayed({ applyPaginationAndScroll(pageInChapter) }, 400)
                }
            }
        }

        LaunchedEffect(
            spineUrls,
            measuredViewportCss,
            epubFile.lastModified(),
            padTopCss,
            padLeftCss,
            padRightCss,
            padBottomCss,
            contentWcss,
        ) {
            // Only fingerprint/measure after real WebView size — avoids constraintW vs measured 1px mismatch.
            if (spineUrls.isEmpty()) {
                layoutLoading = false
                return@LaunchedEffect
            }
            val m = measuredViewportCss
            if (m == null || m.first <= 0 || m.second <= 0) {
                layoutLoading = true
                return@LaunchedEffect
            }
            val layoutW = m.first
            val layoutH = m.second
            layoutProgress = 0 to 0
            layoutLoading = true
            val fp = EpubColumnLayoutCache.fingerprint(
                epubFile,
                layoutW,
                layoutH,
                padTopCss,
                padLeftCss,
                padRightCss,
                padBottomCss,
            )
            val cached = EpubColumnLayoutCache.load(context, book.id, fp)
            if (cached != null && cached.isNotEmpty()) {
                chapterPageCounts = cached
                installReadingClient()
                layoutLoading = false
                return@LaunchedEffect
            }
            chapterPageCounts = emptyList()
            val built = measureEpubChapterPageCounts(
                webView = webView,
                spineUrls = spineUrls,
                viewportWidthPx = layoutW,
                viewportHeightPx = layoutH,
                padTopPx = padTopCss,
                padLeftPx = padLeftCss,
                padRightPx = padRightCss,
                padBottomPx = padBottomCss,
                backgroundCss = epubPageStyle.backgroundCss,
                foregroundCss = epubPageStyle.foregroundCss,
                applyTint = { w ->
                    w.setBackgroundColor(Color.parseColor(epubPageStyle.backgroundCss))
                },
                onChapterProgress = { cur, tot -> layoutProgress = cur to tot },
            )
            EpubColumnLayoutCache.save(context, book.id, fp, built)
            installReadingClient()
            chapterPageCounts = built
            layoutLoading = false
        }

        LaunchedEffect(chapterPageCounts) {
            if (chapterPageCounts.isEmpty()) {
                didInitPosition = false
                return@LaunchedEffect
            }
            if (!didInitPosition) {
                val total = totalBookPages()
                val targetGlobal = (book.readProgress01 * (total - 1).coerceAtLeast(1)).roundToInt()
                    .coerceIn(0, (total - 1).coerceAtLeast(0))
                var acc = 0
                for ((idx, ch) in chapterPageCounts.withIndex()) {
                    if (acc + ch.pageCount > targetGlobal) {
                        spineIndex = idx
                        pageInChapter = targetGlobal - acc
                        break
                    }
                    acc += ch.pageCount
                }
                didInitPosition = true
            }
        }

        val url = spineUrls.getOrNull(spineIndex)
        LaunchedEffect(spineIndex, chapterPageCounts) {
            if (chapterPageCounts.isEmpty()) return@LaunchedEffect
            swipeLocked = true
            if (url != null) webView.loadUrl(url)
            delay(450)
            swipeLocked = false
        }

        LaunchedEffect(
            css,
            pageInChapter,
            spineIndex,
            chapterPageCounts,
            layoutLoading,
            contentWcss,
            viewportWidthCssPx,
            viewportHeightCssPx,
            padLeftCss,
            padRightCss,
            padTopCss,
            padBottomCss,
        ) {
            if (layoutLoading || chapterPageCounts.isEmpty()) return@LaunchedEffect
            webView.post {
                injectEpubPaginationCss(
                    webView,
                    latestCss,
                    latestContentW,
                    latestVw,
                    latestVh,
                    latestPt,
                    latestPr,
                    latestPb,
                    latestPl,
                )
                scrollEpubToPageIndex(webView, pageInChapter, latestContentW)
            }
        }

        LaunchedEffect(epubPageStyle, url) {
            webView.setBackgroundColor(Color.parseColor(epubPageStyle.backgroundCss))
        }

        fun goNextPage() {
            if (swipeLocked || chapterPageCounts.isEmpty()) return
            val pagesHere = chapterPageCounts.getOrNull(spineIndex)?.pageCount ?: 1
            if (pageInChapter < pagesHere - 1) {
                pageInChapter++
            } else if (spineIndex < spineUrls.lastIndex) {
                spineIndex++
                pageInChapter = 0
            }
        }

        fun goPrevPage() {
            if (swipeLocked || chapterPageCounts.isEmpty()) return
            if (pageInChapter > 0) {
                pageInChapter--
            } else if (spineIndex > 0) {
                spineIndex--
                pageInChapter = (chapterPageCounts.getOrNull(spineIndex)?.pageCount ?: 1) - 1
            }
        }

        fun goToGlobalPage(target: Int) {
            if (chapterPageCounts.isEmpty()) return
            var acc = 0
            for ((idx, ch) in chapterPageCounts.withIndex()) {
                if (acc + ch.pageCount > target) {
                    val newPage = target - acc
                    if (idx != spineIndex) {
                        spineIndex = idx
                        pageInChapter = newPage
                    } else {
                        pageInChapter = newPage
                    }
                    return
                }
                acc += ch.pageCount
            }
            spineIndex = chapterPageCounts.lastIndex
            pageInChapter = (chapterPageCounts.lastOrNull()?.pageCount ?: 1) - 1
        }

        val totalPages = totalBookPages()
        val currentPage = globalPageIndex() + 1
        val epubProgress = if (totalPages > 1) {
            (currentPage - 1).toFloat() / (totalPages - 1).toFloat()
        } else {
            0f
        }
        val epubPositionLabel = "Page $currentPage / $totalPages"

        val wpmTracker = remember(book.id, totalPages, infoData.wordsCount) {
            val prefs = context.getSharedPreferences(ReadingWpm.PREFS_NAME, Context.MODE_PRIVATE)
            ReadingWpm.ensureBookWpmSeeded(context, book.id)
            val seed = prefs.getFloat("${ReadingWpm.PREFIX_AVG_WPM_BOOK}${book.id}", ReadingWpm.DEFAULT_WPM)
            ReadingSpeedTracker(
                totalWords = infoData.wordsCount.coerceAtLeast(1),
                totalUnits = totalPages.coerceAtLeast(1),
                seedWpm = seed,
            )
        }
        LaunchedEffect(currentPage, totalPages) {
            if (totalPages > 0) wpmTracker.onUnitIndexChanged((currentPage - 1).coerceAtLeast(0))
            if (totalPages <= 0) return@LaunchedEffect
            val p = if (totalPages > 1) {
                (currentPage - 1).toFloat() / (totalPages - 1).toFloat()
            } else {
                0f
            }
            onReadingProgress(p.coerceIn(0f, 1f))
        }

        LaunchedEffect(book.id) {
            totalReadingSeconds = ReaderReadingTime.loadTotalSeconds(context, book.id)
            sessionReadingSeconds = 0L
        }

        LaunchedEffect(book.id) {
            val prefs = context.getSharedPreferences(ReadingWpm.PREFS_NAME, Context.MODE_PRIVATE)
            val bookKey = "book_days_${book.id}"
            val today = LocalDate.now().toString()
            val daySet = prefs.getStringSet(bookKey, emptySet())?.toMutableSet() ?: mutableSetOf()
            daySet.add(today)
            prefs.edit().putStringSet(bookKey, daySet).apply()
            historyDays = daySet.size
            ReadingWpm.ensureBookWpmSeeded(context, book.id)
            avgWpmUser = prefs.getFloat(ReadingWpm.KEY_AVG_WPM_USER, 0f)
            avgWpmBook = prefs.getFloat("${ReadingWpm.PREFIX_AVG_WPM_BOOK}${book.id}", 0f)
            val (words, chars) = withContext(Dispatchers.IO) { estimateWordAndCharCountForEpubReader(epubFile) }
            infoData = infoData.copy(
                wordsCount = words,
                charsCount = chars,
                fileName = epubFile.name,
                filePath = epubFile.absolutePath,
                fileSizeBytes = epubFile.length(),
            )
        }

        LaunchedEffect(spineIndex, totalPages, currentPage) {
            infoData = infoData.copy(
                totalUnits = totalPages,
                currentUnit = currentPage,
                unitDisplay = BookInfoUnitDisplay.PAGES,
            )
        }

        LaunchedEffect(book.id) {
            while (true) {
                delay(1000)
                sessionReadingSeconds += 1
                totalReadingSeconds += 1
                ReaderReadingTime.saveTotalSeconds(context, book.id, totalReadingSeconds)
                wpmTracker.onTickSecond()
                val prefs = context.getSharedPreferences(ReadingWpm.PREFS_NAME, Context.MODE_PRIVATE)
                val wordsRead = (infoData.wordsCount * epubProgress).toInt()
                val wpm = wpmTracker.sessionWpm(wordsRead)
                if (wpm > 1f) {
                    ReadingWpm.persistSessionSample(prefs, book.id, wpm)
                    avgWpmUser = prefs.getFloat(ReadingWpm.KEY_AVG_WPM_USER, 0f)
                    avgWpmBook = prefs.getFloat("${ReadingWpm.PREFIX_AVG_WPM_BOOK}${book.id}", 0f)
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = sideBorderDp,
                            end = sideBorderDp,
                            top = topBorderDp,
                            bottom = topBorderDp,
                        )
                        .clipToBounds()
                        .onGloballyPositioned { coords ->
                            val w = floor(coords.size.width / densityScale).toInt().coerceAtLeast(1)
                            val h = floor(coords.size.height / densityScale).toInt().coerceAtLeast(1)
                            val cur = measuredViewportCss
                            if (cur == null || cur.first != w || cur.second != h) {
                                measuredViewportCss = w to h
                            }
                        },
                ) {
                    AndroidView(
                        factory = { ctx ->
                            EpubSwipeLayout(ctx).apply {
                                clipChildren = true
                                clipToPadding = true
                                layoutParams = android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                )
                                addView(
                                    webView,
                                    android.widget.FrameLayout.LayoutParams(
                                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    ),
                                )
                            }
                        },
                        update = { layout ->
                            val th = with(density) { 22.dp.toPx() }
                            layout.swipeThresholdPx = th
                            layout.swipeThresholdBackPx = th
                            layout.onSwipeLeft = { goNextPage() }
                            layout.onSwipeRight = { goPrevPage() }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                EpubReaderBorderOverlay()
                if (layoutLoading || (chapterPageCounts.isEmpty() && spineUrls.isNotEmpty())) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            val (cur, tot) = layoutProgress
                            if (tot > 0) {
                                Text(
                                    text = "Preparing pages $cur / $tot",
                                    modifier = Modifier.padding(top = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
                if (chromeVisible) {
                    ReaderDismissTapLayer(onDismiss = onToggleChrome)
                }
                ReaderCenterTapZone(onToggleChrome = onToggleChrome)
            }
            if (chromeVisible) {
                ReaderChromeBottomDock(
                    progress01 = epubProgress,
                    progressLabel = epubPositionLabel,
                    onSeekFraction = { fraction ->
                        val t = totalBookPages()
                        if (t <= 1) return@ReaderChromeBottomDock
                        val targetGlobal = (fraction.coerceIn(0f, 1f) * (t - 1))
                            .roundToInt()
                            .coerceIn(0, t - 1)
                        goToGlobalPage(targetGlobal)
                    },
                    onSeekingChanged = { wpmTracker.setScrubbing(it) },
                    onOpenThemeMenu = { onToggleChrome() },
                    onOpenBookMenu = { onToggleChrome() },
                    onShowBookInfo = { showBookInfo = true },
                    onShutdownApp = onBack,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else if (
                !showBookInfo &&
                !layoutLoading &&
                chapterPageCounts.isNotEmpty()
            ) {
                val chapterTotalPages = (chapterPageCounts.getOrNull(spineIndex)?.pageCount ?: 1).coerceAtLeast(1)
                val currentPageInChapter = (pageInChapter + 1).coerceAtLeast(1)
                val remainingUnitsChapter = (chapterTotalPages - currentPageInChapter + 1).coerceAtLeast(0)
                val etaInfo = computeReaderEtaInfo(
                    wordsCount = infoData.wordsCount,
                    progress01Book = epubProgress,
                    totalUnitsBook = totalPages.coerceAtLeast(1),
                    validReadingSecondsForWpm = wpmTracker.validReadSeconds(),
                    sessionReadingSeconds = sessionReadingSeconds,
                    avgWpmUser = avgWpmUser,
                    avgWpmBook = avgWpmBook,
                    remainingUnitsChapter = remainingUnitsChapter,
                )
                ReaderMinimalStatusBar(
                    chapterTitle = "Chapter ${spineIndex + 1}",
                    chapterPageText = "$currentPageInChapter / $chapterTotalPages",
                    readProgress01 = epubProgress,
                    etaInfo = etaInfo,
                    contentColor = readerTextColor,
                    backgroundColor = readerPageBg,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            if (showBookInfo) {
                BookInfoPageOverlay(
                    book = book,
                    info = infoData,
                    coverPath = book.coverPath,
                    totalReadingSeconds = totalReadingSeconds,
                    sessionReadingSeconds = sessionReadingSeconds,
                    validReadingSecondsForWpm = wpmTracker.validReadSeconds(),
                    historyDays = historyDays,
                    avgWpmUser = avgWpmUser,
                    avgWpmBook = avgWpmBook,
                    currentProgress01 = epubProgress,
                    onOpenChapterLink = null,
                    onDismiss = { showBookInfo = false },
                    onRatingChanged = onRatingChanged,
                )
            }
        }
    }
}

private fun estimateWordAndCharCountForEpubReader(file: File): Pair<Int, Int> = runCatching {
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

