@file:OptIn(
    org.readium.r2.shared.ExperimentalReadiumApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.example.optireader.ui

import android.graphics.Color
import android.util.Log
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.example.optireader.R
import com.example.optireader.data.AppDatabase
import com.example.optireader.data.BookEntity
import com.example.optireader.data.UserDictionaryDao
import com.example.optireader.data.UserDictionaryEntity
import com.example.optireader.epub.EpubOpfParser
import com.example.optireader.reading.ReadingSpeedTracker
import com.example.optireader.reading.ReadingWpm
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.StringSearchService
import org.readium.r2.shared.publication.services.search.searchServiceFactory
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

@Composable
fun ReadiumEpubReaderHost(
    book: BookEntity,
    epubPageStyle: EpubPageStyle,
    onBack: () -> Unit,
    onReadingProgress: (Float) -> Unit,
    onRatingChanged: (Float?) -> Unit = {},
    navigatorResumeKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var chromeVisible by remember(book.id) { mutableStateOf(false) }
    var publication by remember(book.id) { mutableStateOf<Publication?>(null) }
    var spineUrls by remember(book.id) { mutableStateOf<List<String>>(emptyList()) }
    var tocEntries by remember(book.id) { mutableStateOf<List<TocEntry>>(emptyList()) }
    var spineIndex by remember(book.id) { mutableStateOf(0) }
    var loadError by remember(book.id) { mutableStateOf<String?>(null) }
    var showBookInfo by remember(book.id) { mutableStateOf(false) }
    var showBookSettings by remember(book.id) { mutableStateOf(false) }
    var dictionaryDialogState by remember(book.id) { mutableStateOf<DictionaryDialogState?>(null) }
    var totalReadingSeconds by remember(book.id) { mutableStateOf(0L) }
    var sessionReadingSeconds by remember(book.id) { mutableStateOf(0L) }
    var avgWpmUser by remember { mutableStateOf(0f) }
    var avgWpmBook by remember { mutableStateOf(0f) }
    var historyDays by remember(book.id) { mutableStateOf(1) }
    var infoData by remember(book.id) {
        mutableStateOf(
            BookInfoUiData(
                chapters = emptyList(),
                chapterPageIndexBySpine = emptyMap(),
                title = book.title.ifBlank { fileNameFromPath(book.localPath) },
                author = "Unknown",
                creator = "Unknown",
                fileName = fileNameFromPath(book.localPath),
                filePath = book.localPath,
                fileSizeBytes = File(book.localPath).length(),
                totalUnits = 0,
                currentUnit = 1,
                wordsCount = 0,
                charsCount = 0,
            ),
        )
    }
    var globalPageView by remember(book.id) { mutableStateOf(false) }
    var bookPageViewOverride by remember(book.id) { mutableStateOf<Boolean?>(null) }
    val effectivePageView = bookPageViewOverride ?: globalPageView
    var epubTheme by remember(book.id) {
        mutableStateOf(loadEpubTheme(context, book.id, epubPageStyle))
    }
    ReaderStatusBarAppearanceEffect(
        contentColor = composeColorFromArgb(epubTheme.textArgb),
        windowBarBackgroundColor = composeColorFromArgb(epubTheme.pageArgb),
    )
    var showEpubTheme by remember(book.id) { mutableStateOf(false) }
    var epubColorTarget by remember(book.id) { mutableStateOf<EpubColorTarget?>(null) }
    var epubNavFragment by remember(book.id) { mutableStateOf<EpubNavigatorFragment?>(null) }
    var volumeOverlayPct by remember { mutableStateOf<Int?>(null) }
    var epubCurrentLocator by remember(book.id) { mutableStateOf<Locator?>(null) }
    var bookmarks by remember(book.id) { mutableStateOf<List<BookmarkEntry>>(emptyList()) }
    var bookmarkRefreshKey by remember(book.id) { mutableIntStateOf(0) }
    val readerOptionsPrefsBookmarks = remember {
        context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE)
    }
    var bookmarkColorArgb by remember(book.id) {
        mutableIntStateOf(loadBookmarkIconColorArgb(readerOptionsPrefsBookmarks))
    }
    DisposableEffect(readerOptionsPrefsBookmarks) {
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BOOKMARK_ICON_COLOR_ARGB || key == null) {
                bookmarkColorArgb = loadBookmarkIconColorArgb(readerOptionsPrefsBookmarks)
            }
        }
        readerOptionsPrefsBookmarks.registerOnSharedPreferenceChangeListener(l)
        onDispose { readerOptionsPrefsBookmarks.unregisterOnSharedPreferenceChangeListener(l) }
    }
    LaunchedEffect(book.id, bookmarkRefreshKey) {
        bookmarks = BookmarkStore.load(context, book.id)
    }
    var epubHighlights by remember(book.id) { mutableStateOf<List<Decoration>>(emptyList()) }
    /** Flat publication positions; used for global progress + locator mapping (font-independent). */
    var epubPositionLocators by remember(book.id) { mutableStateOf<List<Locator>>(emptyList()) }
    var epubPageIndex by remember(book.id) { mutableStateOf(0) }

    var globalEpubTypography by remember {
        mutableStateOf(loadReaderGlobalEpubTypography(context))
    }
    var globalParagraphSpacingPercent by remember {
        mutableStateOf(loadParagraphSpacingPercent(context))
    }
    var globalLineSpacingStep by remember {
        mutableStateOf(loadLineSpacingStep(context))
    }
    var readerControls by remember(book.id) {
        mutableStateOf(
            loadBookReaderControlsSettings(
                context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE),
                book.id,
            ),
        )
    }
    var showAddNoteDialog by remember(book.id) { mutableStateOf(false) }
    var pendingNoteSelection by remember(book.id) { mutableStateOf("") }
    var pendingNoteBody by remember(book.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    DisposableEffect(book.id) {
        onDispose {
            val activity = context as? FragmentActivity
            val fm = activity?.supportFragmentManager ?: return@onDispose
            val prefix = "readium_nav_${book.id}_"
            val fragments = fm.fragments.filter { it.tag?.startsWith(prefix) == true }
            if (fragments.isNotEmpty()) {
                val tx = fm.beginTransaction()
                fragments.forEach { tx.remove(it) }
                tx.commitAllowingStateLoss()
            }
        }
    }
    val userDictDao = remember(book.id) { AppDatabase.get(context).userDictionaryDao() }
    var tts by remember(book.id) { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(book.id) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
    val selectionActionModeCallback = remember(book.id) {
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.selection_context_menu, menu)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode) = Unit
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                scope.launch {
                    when (item.itemId) {
                        R.id.menu_select_all -> {
                            epubNavFragment?.evaluateJavascript(
                                """
                                (function() {
                                  var range = document.createRange();
                                  range.selectNodeContents(document.body);
                                  var sel = window.getSelection();
                                  if (!sel) return;
                                  sel.removeAllRanges();
                                  sel.addRange(range);
                                })();
                                """.trimIndent(),
                            )
                        }
                        else -> {
                            val selected = readSelectionText(epubNavFragment).trim()
                            if (selected.isBlank()) return@launch
                            when (item.itemId) {
                                R.id.menu_copy -> {
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("selection", selected))
                                }
                                R.id.menu_translate -> context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://translate.google.com/?sl=auto&tl=en&text=${Uri.encode(selected)}&op=translate"),
                                    ),
                                )
                                R.id.menu_share -> context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, selected)
                                        },
                                        "Share text",
                                    ),
                                )
                                R.id.menu_highlight -> Unit
                                R.id.menu_note -> Unit
                                R.id.menu_google -> context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.google.com/search?q=${Uri.encode(selected)}"),
                                    ),
                                )
                                R.id.menu_read_aloud -> {
                                    val engine = tts ?: TextToSpeech(context) { status ->
                                        if (status == TextToSpeech.SUCCESS) {
                                            tts?.language = Locale.getDefault()
                                            tts?.speak(selected, TextToSpeech.QUEUE_FLUSH, null, "selection_read_aloud")
                                        }
                                    }.also { tts = it }
                                    engine.language = Locale.getDefault()
                                    engine.speak(selected, TextToSpeech.QUEUE_FLUSH, null, "selection_read_aloud")
                                }
                                R.id.menu_dictionary -> Unit
                            }
                            if (item.itemId == R.id.menu_dictionary) {
                                when (val r = lookupOfflineWordnetDefinitions(context, selected, userDictDao)) {
                                    is OfflineDictResult.Ok -> {
                                        dictionaryDialogState = DictionaryDialogState.Definitions(r.lemma, r.definitions)
                                    }
                                    is OfflineDictResult.MissingDb -> {
                                        Toast.makeText(
                                            context,
                                            "Offline dictionary: database not installed (${r.detail}).",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                    is OfflineDictResult.NoEntry -> {
                                        if (r.lemma.isBlank()) {
                                            Toast.makeText(
                                                context,
                                                "No word selected for the dictionary.",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            dictionaryDialogState = DictionaryDialogState.NotFound(r.lemma)
                                        }
                                    }
                                    is OfflineDictResult.DbError -> {
                                        Toast.makeText(
                                            context,
                                            "Offline dictionary error: ${r.message}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            }
                            if (item.itemId == R.id.menu_highlight) {
                                val sel = epubNavFragment?.currentSelection()
                                if (sel != null) {
                                    val deco = Decoration(
                                        id = "hl_${System.currentTimeMillis()}",
                                        locator = sel.locator,
                                        style = Decoration.Style.Highlight(tint = epubTheme.highlightArgb),
                                    )
                                    val updated = epubHighlights + deco
                                    epubHighlights = updated
                                    epubNavFragment?.applyDecorations(updated, "user-highlights")
                                    savePersistedHighlights(context, book.id, updated)
                                    BookStatsStore.addHighlight(
                                        context = context,
                                        bookId = book.id,
                                        selectedText = selected,
                                        paragraph = sel.locator.text?.before + selected + sel.locator.text?.after,
                                    )
                                    epubNavFragment?.clearSelection()
                                }
                            }
                            if (item.itemId == R.id.menu_note) {
                                pendingNoteSelection = selected
                                pendingNoteBody = ""
                                showAddNoteDialog = true
                            }
                        }
                    }
                    mode.finish()
                }
                return true
            }
        }
    }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (
                key == "epub_global_text_bold" ||
                key == "epub_global_text_italic" ||
                key == "epub_global_text_underline" ||
                key == "epub_global_text_shadow"
            ) {
                globalEpubTypography = loadReaderGlobalEpubTypography(context)
            }
            if (
                key == KEY_EPUB_GLOBAL_PARAGRAPH_SPACING_PERCENT ||
                key == KEY_EPUB_GLOBAL_LINE_SPACING_STEP
            ) {
                globalParagraphSpacingPercent = loadParagraphSpacingPercent(context)
                globalLineSpacingStep = loadLineSpacingStep(context)
            }
            readerControls = loadBookReaderControlsSettings(prefs, book.id)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(book.id) {
        totalReadingSeconds = ReaderReadingTime.loadTotalSeconds(context, book.id)
        sessionReadingSeconds = 0L
    }

    LaunchedEffect(book.id, epubPageStyle.backgroundCss, epubPageStyle.foregroundCss) {
        epubTheme = loadEpubTheme(context, book.id, epubPageStyle)
    }
    LaunchedEffect(book.id) {
        epubHighlights = loadPersistedHighlights(context, book.id)
    }


    LaunchedEffect(
        epubTheme,
        epubNavFragment,
        effectivePageView,
        globalEpubTypography.bold,
        globalParagraphSpacingPercent,
        globalLineSpacingStep,
    ) {
        epubNavFragment?.submitPreferences(
            epubTheme.toEpubPreferences(
                scroll = !effectivePageView,
                globalBold = globalEpubTypography.bold,
                paragraphSpacingPercent = globalParagraphSpacingPercent,
                lineSpacingStep = globalLineSpacingStep,
            ),
        )
    }

    LaunchedEffect(epubNavFragment, globalEpubTypography, epubPageIndex) {
        val f = epubNavFragment ?: return@LaunchedEffect
        val css = buildGlobalEpubTypographyCss(globalEpubTypography)
        f.injectGlobalEpubTypographyCss(css)
    }
    LaunchedEffect(epubNavFragment, epubHighlights) {
        val f = epubNavFragment ?: return@LaunchedEffect
        if (epubHighlights.isNotEmpty()) {
            f.applyDecorations(epubHighlights, "user-highlights")
        }
    }

    LaunchedEffect(book.id, book.localPath) {
        val prefs = context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE)
        globalPageView = prefs.getBoolean("global_page_view_default", false)
        bookPageViewOverride = if (prefs.contains("book_page_view_override_epub_${book.id}")) {
            prefs.getBoolean("book_page_view_override_epub_${book.id}", false)
        } else null

        loadError = null
        val file = File(book.localPath)
        if (!file.isFile) {
            loadError = "EPUB file not found."
            return@LaunchedEffect
        }
        runCatching {
            val httpClient = DefaultHttpClient()
            val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
            val asset = assetRetriever.retrieve(file)
                .getOrElse { error("Failed to retrieve EPUB asset: ${it.message}") }
            val parser = DefaultPublicationParser(context, httpClient, assetRetriever, null)
            val opener = PublicationOpener(parser) {
                // Ensure Readium search is available for chapter-by-chapter results iteration.
                servicesBuilder.searchServiceFactory = StringSearchService.createDefaultFactory()
            }
            val pub = opener.open(asset, allowUserInteraction = true)
                .getOrElse { error("Failed to open EPUB publication: ${it.message}") }
            publication = pub
            spineUrls = pub.readingOrder.map { it.href.toString() }
            tocEntries = extractTocEntries(pub)
            spineIndex = 0
        }.onFailure {
            loadError = it.message ?: "Failed to open EPUB."
        }
    }

    LaunchedEffect(
        effectivePageView,
        epubPageIndex,
        epubPositionLocators.size,
        spineIndex,
        spineUrls.size,
    ) {
        when {
            effectivePageView && epubPositionLocators.isNotEmpty() -> {
                val n = epubPositionLocators.size
                val progress = if (n <= 1) 1f else (epubPageIndex + 1).toFloat() / n.toFloat()
                onReadingProgress(progress.coerceIn(0f, 1f))
            }
            !effectivePageView && spineUrls.isNotEmpty() -> {
                val progress = if (spineUrls.size == 1) 1f else spineIndex.toFloat() / (spineUrls.size - 1).toFloat()
                onReadingProgress(progress.coerceIn(0f, 1f))
            }
        }
    }

    LaunchedEffect(publication, effectivePageView, book.id) {
        val pub = publication
        if (pub == null) {
            epubPositionLocators = emptyList()
            return@LaunchedEffect
        }
        if (effectivePageView) {
            epubPositionLocators = withContext(Dispatchers.IO) {
                runCatching { pub.positions() }.getOrElse { emptyList() }
            }
        } else {
            epubPositionLocators = emptyList()
        }
    }

    LaunchedEffect(epubNavFragment, epubPositionLocators, effectivePageView, book.id) {
        val frag = epubNavFragment ?: return@LaunchedEffect
        frag.currentLocator.collect { loc ->
            epubCurrentLocator = loc
            val currentSpine = resolveSpineIndexForHref(spineUrls, loc.href.toString())
            if (currentSpine >= 0) {
                spineIndex = currentSpine
            }
            if (effectivePageView && epubPositionLocators.isNotEmpty()) {
                epubPageIndex = indexOfLocatorInPublicationPositions(epubPositionLocators, loc)
                    .coerceIn(0, (epubPositionLocators.size - 1).coerceAtLeast(0))
            }
        }
    }

    var didApplySavedReadProgress by remember(book.id, book.readProgress01, navigatorResumeKey) {
        mutableStateOf(false)
    }
    LaunchedEffect(
        epubNavFragment,
        epubPositionLocators,
        spineUrls,
        effectivePageView,
        publication,
        book.readProgress01,
        navigatorResumeKey,
    ) {
        if (didApplySavedReadProgress) return@LaunchedEffect
        val nav = epubNavFragment ?: return@LaunchedEffect
        val pub = publication ?: return@LaunchedEffect
        val latestBm = BookmarkStore.getLatest(context, book.id)
        if (latestBm != null && latestBm.format == "epub" && !latestBm.locatorJsonString.isNullOrBlank()) {
            val bmLoc = runCatching { Locator.fromJSON(JSONObject(latestBm.locatorJsonString)) }.getOrNull()
            if (bmLoc != null) {
                delay(80)
                runCatching { nav.go(bmLoc, animated = false) }
                didApplySavedReadProgress = true
                return@LaunchedEffect
            }
        }
        val p = book.readProgress01.coerceIn(0f, 1f)
        if (p <= 0.0005f) {
            didApplySavedReadProgress = true
            return@LaunchedEffect
        }
        when {
            effectivePageView && epubPositionLocators.isEmpty() -> return@LaunchedEffect
            effectivePageView && epubPositionLocators.size > 1 -> {
                delay(80)
                val locs = epubPositionLocators
                val idx = (p * (locs.size - 1)).roundToInt().coerceIn(0, locs.size - 1)
                runCatching { nav.go(locs[idx], animated = false) }
                didApplySavedReadProgress = true
            }
            effectivePageView -> {
                didApplySavedReadProgress = true
            }
            !effectivePageView && spineUrls.size > 1 -> {
                delay(80)
                val spineIdx = (p * spineUrls.lastIndex).roundToInt().coerceIn(0, spineUrls.lastIndex)
                val link = pub.readingOrder.getOrNull(spineIdx)
                val loc = link?.let { pub.locatorFromLink(it) }
                if (loc != null) {
                    runCatching { nav.go(loc, animated = false) }
                }
                didApplySavedReadProgress = true
            }
            else -> {
                didApplySavedReadProgress = true
            }
        }
    }

    LaunchedEffect(book.id, spineUrls) {
        val file = File(book.localPath)
        if (!file.isFile) return@LaunchedEffect
        val titleFallback = book.title.ifBlank { fileNameFromPath(book.localPath) }
        val prefs = context.getSharedPreferences("reader_stats", android.content.Context.MODE_PRIVATE)
        val key = "book_days_${book.id}"
        val today = LocalDate.now().toString()
        val daySet = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        daySet.add(today)
        prefs.edit().putStringSet(key, daySet).apply()
        historyDays = daySet.size
        ReadingWpm.ensureBookWpmSeeded(context, book.id)
        avgWpmUser = prefs.getFloat(ReadingWpm.KEY_AVG_WPM_USER, 0f)
        avgWpmBook = prefs.getFloat("${ReadingWpm.PREFIX_AVG_WPM_BOOK}${book.id}", 0f)

        val title = withContext(Dispatchers.IO) {
            runCatching {
                ZipFile(file).use { zip ->
                    EpubOpfParser.readPackage(zip)?.title?.takeIf { it.isNotBlank() } ?: titleFallback
                }
            }.getOrElse { titleFallback }
        }
        val (words, chars) = withContext(Dispatchers.IO) { estimateWordAndCharCountForEpub(file) }
        val firstMainChapterIndex = detectFirstMainChapterIndex(spineUrls, tocEntries)
        infoData = infoData.copy(
            title = title,
            fileName = file.name,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            wordsCount = words,
            charsCount = chars,
            chapters = if (tocEntries.isNotEmpty()) {
                tocEntries
            } else {
                spineUrls.mapIndexed { i, href ->
                    TocEntry(formatChapterLabel(i, firstMainChapterIndex), href)
                }
            },
        )
    }

    LaunchedEffect(
        effectivePageView,
        epubPositionLocators.size,
        epubPageIndex,
        spineIndex,
        spineUrls.size,
    ) {
        if (spineUrls.isEmpty()) return@LaunchedEffect
        val (total, current, display) = when {
            effectivePageView && epubPositionLocators.isNotEmpty() -> {
                Triple(
                    epubPositionLocators.size.coerceAtLeast(1),
                    (epubPageIndex + 1).coerceAtLeast(1),
                    BookInfoUnitDisplay.PAGES,
                )
            }
            else -> {
                Triple(
                    spineUrls.size.coerceAtLeast(1),
                    (spineIndex + 1).coerceAtLeast(1),
                    BookInfoUnitDisplay.CHAPTERS,
                )
            }
        }
        infoData = infoData.copy(
            totalUnits = total,
            currentUnit = current,
            unitDisplay = display,
        )
    }

    val posCount = epubPositionLocators.size
    val wpmUnitCount = if (effectivePageView && posCount > 0) posCount else spineUrls.size.coerceAtLeast(1)
    val wpmUnitIndex = if (effectivePageView && epubPositionLocators.isNotEmpty()) epubPageIndex else spineIndex

    val wpmTracker = remember(book.id, infoData.wordsCount, wpmUnitCount) {
        val prefs = context.getSharedPreferences(ReadingWpm.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        ReadingWpm.ensureBookWpmSeeded(context, book.id)
        val seed = prefs.getFloat("${ReadingWpm.PREFIX_AVG_WPM_BOOK}${book.id}", ReadingWpm.DEFAULT_WPM)
        ReadingSpeedTracker(
            totalWords = infoData.wordsCount.coerceAtLeast(1),
            totalUnits = wpmUnitCount.coerceAtLeast(1),
            seedWpm = seed,
        )
    }
    LaunchedEffect(wpmUnitIndex, wpmUnitCount) {
        if (wpmUnitCount > 0) wpmTracker.onUnitIndexChanged(wpmUnitIndex.coerceAtLeast(0))
    }

    LaunchedEffect(book.id, spineUrls.size) {
        if (spineUrls.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(1000)
            sessionReadingSeconds += 1
            totalReadingSeconds += 1
            ReaderReadingTime.saveTotalSeconds(context, book.id, totalReadingSeconds)
            wpmTracker.onTickSecond()
            val prefs = context.getSharedPreferences(ReadingWpm.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val readiumProgress = when {
                effectivePageView && epubPositionLocators.isNotEmpty() -> {
                    val n = epubPositionLocators.size
                    if (n <= 1) 1f else epubPageIndex.toFloat() / (n - 1).coerceAtLeast(1)
                }
                spineUrls.size <= 1 -> 1f
                else -> spineIndex.toFloat() / spineUrls.lastIndex.coerceAtLeast(1)
            }
            val wordsRead = (infoData.wordsCount * readiumProgress).toInt()
            val wpm = wpmTracker.sessionWpm(wordsRead)
            if (wpm > 1f) {
                ReadingWpm.persistSessionSample(prefs, book.id, wpm)
                avgWpmUser = prefs.getFloat(ReadingWpm.KEY_AVG_WPM_USER, 0f)
                avgWpmBook = prefs.getFloat("${ReadingWpm.PREFIX_AVG_WPM_BOOK}${book.id}", 0f)
            }
        }
    }

    ReaderVolumeKeysInterceptor(
        controls = readerControls,
        onForward = { epubNavFragment?.goForward(animated = true) },
        onBackward = { epubNavFragment?.goBackward(animated = true) },
    ) { keyMod ->
    Box(
        modifier = modifier
            .then(keyMod)
            .fillMaxSize()
            .background(composeColorFromArgb(epubTheme.pageArgb)),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(loadError ?: "Failed to open EPUB.", color = MaterialTheme.colorScheme.error)
                    }
                publication == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Preparing EPUB...")
                }
                else -> {
                    val fragmentActivity = context as? FragmentActivity
                    val containerId = remember(book.id) { View.generateViewId() }
                    // Bump suffix when navigator wiring changes so an existing fragment is recreated (e.g. pagination listener).
                    val fragmentTag =
                        "readium_nav_${book.id}_${if (effectivePageView) "page" else "scroll"}_pl_$navigatorResumeKey"
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { ctx -> FragmentContainerView(ctx).apply { id = containerId } },
                            modifier = Modifier
                                .fillMaxSize()
                                .readerEdgeSlideControls(
                                    context = context,
                                    controls = readerControls,
                                    onVolumeChangedPercent = { volumeOverlayPct = it },
                                ),
                            update = { view ->
                                val fm = fragmentActivity?.supportFragmentManager ?: return@AndroidView
                                val existing = fm.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
                                if (existing == null) {
                                    val pub = publication ?: return@AndroidView
                                    val factory = EpubNavigatorFactory(pub).createFragmentFactory(
                                        initialLocator = null,
                                        initialPreferences = epubTheme.toEpubPreferences(
                                            scroll = !effectivePageView,
                                            globalBold = globalEpubTypography.bold,
                                            paragraphSpacingPercent = globalParagraphSpacingPercent,
                                            lineSpacingStep = globalLineSpacingStep,
                                        ),
                                        configuration = EpubNavigatorFragment.Configuration(
                                            selectionActionModeCallback = selectionActionModeCallback,
                                        ),
                                    )
                                    val fragment = factory.instantiate(view.context.classLoader, EpubNavigatorFragment::class.java.name)
                                    fm.beginTransaction()
                                        .replace(containerId, fragment, fragmentTag)
                                        .commitNowAllowingStateLoss()
                                } else if (existing.id != containerId) {
                                    // Reattach an existing navigator fragment to the new container when returning
                                    // to this screen, otherwise the view can remain detached and appear blank.
                                    fm.beginTransaction()
                                        .replace(containerId, existing, fragmentTag)
                                        .commitNowAllowingStateLoss()
                                }
                                view.post {
                                    epubNavFragment = fm.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
                                }
                            },
                        )
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
                }
                }
            }

        if (
            !chromeVisible &&
            !showBookInfo &&
            !showBookSettings &&
            !showEpubTheme &&
            loadError == null &&
            publication != null &&
            spineUrls.isNotEmpty()
        ) {
                val usePageBar = effectivePageView && epubPositionLocators.isNotEmpty()
                val posCount = epubPositionLocators.size
                val firstMainChapterIndex = detectFirstMainChapterIndex(spineUrls, tocEntries)
                val readiumProgress01 = when {
                    usePageBar && posCount > 1 ->
                        epubPageIndex.toFloat() / (posCount - 1).coerceAtLeast(1)
                    usePageBar && posCount <= 1 -> 1f
                    spineUrls.size <= 1 -> 1f
                    else -> spineIndex.toFloat() / spineUrls.lastIndex.coerceAtLeast(1)
                }

                val (chapterTitle, chapterPageText, remainingUnitsChapter) =
                    if (usePageBar && posCount > 0) {
                        val currentHref = epubPositionLocators.getOrNull(epubPageIndex)?.href
                        val chapterIndices = if (currentHref != null) {
                            epubPositionLocators.indices.filter { idx ->
                                epubPositionLocators[idx].href == currentHref
                            }
                        } else {
                            emptyList()
                        }
                        val chapterTotalPages = chapterIndices.size.coerceAtLeast(1)
                        val currentPageInChapter =
                            (chapterIndices.indexOf(epubPageIndex).takeIf { it >= 0 }?.plus(1))
                                ?: 1
                        val chapterSpineIndex = if (currentHref != null) {
                            val i = spineUrls.indexOf(currentHref.toString())
                            if (i >= 0) i else 0
                        } else {
                            0
                        }
                        val chapterLabel = formatChapterLabel(
                            chapterSpineIndex,
                            firstMainChapterIndex,
                        )
                        Triple(
                            chapterLabel,
                            "$currentPageInChapter / $chapterTotalPages",
                            (chapterTotalPages - currentPageInChapter).coerceAtLeast(0),
                        )
                    } else {
                        val chapterLabel = formatChapterLabel(
                            spineIndex.coerceAtLeast(0),
                            firstMainChapterIndex,
                        )
                        Triple(
                            chapterLabel,
                            "1 / 1",
                            1,
                        )
                    }

                val etaInfo = computeReaderEtaInfo(
                    wordsCount = infoData.wordsCount,
                    progress01Book = readiumProgress01,
                    totalUnitsBook = wpmUnitCount.coerceAtLeast(1),
                    validReadingSecondsForWpm = wpmTracker.validReadSeconds(),
                    sessionReadingSeconds = sessionReadingSeconds,
                    avgWpmUser = avgWpmUser,
                    avgWpmBook = avgWpmBook,
                    remainingUnitsChapter = remainingUnitsChapter,
                )

            ReaderMinimalStatusBar(
                chapterTitle = chapterTitle,
                chapterPageText = chapterPageText,
                readProgress01 = readiumProgress01.coerceIn(0f, 1f),
                etaInfo = etaInfo,
                contentColor = composeColorFromArgb(epubTheme.textArgb),
                backgroundColor = composeColorFromArgb(epubTheme.pageArgb),
                controls = readerControls,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        }

        if (chromeVisible && publication != null && loadError == null) {
            val usePageBar = effectivePageView && epubPositionLocators.isNotEmpty()
            val posCount = epubPositionLocators.size
            val firstMainChapterIndex = detectFirstMainChapterIndex(spineUrls, tocEntries)
            val progress01 = when {
                usePageBar && posCount > 1 ->
                    epubPageIndex.toFloat() / (posCount - 1).toFloat()
                usePageBar && posCount <= 1 -> 0f
                spineUrls.size <= 1 -> 0f
                else -> spineIndex.toFloat() / spineUrls.lastIndex.toFloat()
            }
            val barLabel = when {
                usePageBar -> "Page ${epubPageIndex + 1} / $posCount"
                else -> formatChapterLabel(spineIndex, firstMainChapterIndex)
            }
            val bottomInset = WindowInsets.navigationBars
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier
                    .zIndex(4f)
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = with(density) { bottomInset.getBottom(this).toDp() }),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(barLabel)
                    Slider(
                        value = progress01.coerceIn(0f, 1f),
                        onValueChange = { f ->
                            wpmTracker.setScrubbing(true)
                            val x = f.coerceIn(0f, 1f)
                            when {
                                usePageBar && posCount > 1 -> {
                                    val newIdx = (x * (posCount - 1)).toInt().coerceIn(0, posCount - 1)
                                    val target = epubPositionLocators[newIdx]
                                    epubNavFragment?.go(target, animated = true)
                                }
                                spineUrls.isNotEmpty() && !effectivePageView -> {
                                    spineIndex = (x * spineUrls.lastIndex).toInt().coerceIn(0, spineUrls.lastIndex)
                                }
                                else -> Unit
                            }
                        },
                        onValueChangeFinished = { wpmTracker.setScrubbing(false) },
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showEpubTheme = true }) { Icon(Icons.Filled.Palette, null) }
                        IconButton(onClick = { showBookSettings = true }) { Icon(Icons.Filled.Settings, null) }
                        IconButton(onClick = { showBookInfo = true }) { Icon(Icons.Filled.Info, null) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onBack) { Icon(Icons.Filled.PowerSettingsNew, null) }
                    }
                }
            }
        }

        DisposableEffect(epubNavFragment) {
            val fragment = epubNavFragment
            if (fragment == null) return@DisposableEffect onDispose { }
            val tapListener = object : InputListener {
                override fun onTap(event: org.readium.r2.navigator.input.TapEvent): Boolean {
                    chromeVisible = !chromeVisible
                    return true
                }
            }
            fragment.addInputListener(tapListener)
            onDispose {
                fragment.removeInputListener(tapListener)
            }
        }

        if (
            !showBookInfo &&
            !showBookSettings &&
            !showEpubTheme &&
            epubColorTarget == null &&
            loadError == null &&
            publication != null
        ) {
            val locJson = epubCurrentLocator?.toJSON()?.toString()
            val usePageBarBm = effectivePageView && epubPositionLocators.isNotEmpty()
            val posCountBm = epubPositionLocators.size
            val readiumProgressForBm = when {
                usePageBarBm && posCountBm > 1 ->
                    epubPageIndex.toFloat() / (posCountBm - 1).coerceAtLeast(1)
                usePageBarBm && posCountBm <= 1 -> 1f
                spineUrls.size <= 1 -> 1f
                else -> spineIndex.toFloat() / spineUrls.lastIndex.coerceAtLeast(1)
            }
            ReaderBookmarkCornerOverlay(
                bookmarkColorArgb = bookmarkColorArgb,
                showMarker = bookmarks.any { bookmarkMatchesEpubLocator(it, locJson) },
                onCornerTap = {
                    val loc = epubCurrentLocator ?: return@ReaderBookmarkCornerOverlay
                    BookmarkStore.addEpub(context, book.id, loc, readiumProgressForBm.coerceIn(0f, 1f))
                    bookmarkRefreshKey++
                },
            )
        }

        if (showBookInfo) {
            val progress = when {
                effectivePageView && epubPositionLocators.isNotEmpty() -> {
                    val n = epubPositionLocators.size
                    if (n <= 1) 1f else (epubPageIndex + 1).toFloat() / n.toFloat()
                }
                spineUrls.size <= 1 -> 1f
                else -> spineIndex.toFloat() / spineUrls.lastIndex.toFloat()
            }
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
                currentProgress01 = progress.coerceIn(0f, 1f),
                onOpenChapterLink = null,
                onDismiss = { showBookInfo = false },
                onRatingChanged = onRatingChanged,
                bookmarks = bookmarks,
                onBookmarkSelected = { bm ->
                    showBookInfo = false
                    if (bm.format == "epub" && !bm.locatorJsonString.isNullOrBlank()) {
                        val target = runCatching { Locator.fromJSON(JSONObject(bm.locatorJsonString)) }.getOrNull()
                        if (target != null) {
                            scope.launch { epubNavFragment?.go(target, animated = true) }
                        }
                    }
                },
            )
        }
        if (showAddNoteDialog) {
            AlertDialog(
                onDismissRequest = { showAddNoteDialog = false },
                title = { Text("Add note") },
                text = {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = pendingNoteSelection,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                        )
                        OutlinedTextField(
                            value = pendingNoteBody,
                            onValueChange = { pendingNoteBody = it },
                            label = { Text("Note") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            BookStatsStore.addInBookNote(
                                context = context,
                                bookId = book.id,
                                anchorText = pendingNoteSelection,
                                note = pendingNoteBody,
                            )
                            showAddNoteDialog = false
                        },
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddNoteDialog = false }) { Text("Cancel") }
                },
            )
        }

        if (showEpubTheme) {
            EpubReaderThemeSheet(
                bookId = book.id,
                theme = epubTheme,
                onThemeChange = { next ->
                    epubTheme = next
                    saveEpubTheme(context, book.id, next)
                    epubNavFragment?.submitPreferences(
                        next.toEpubPreferences(
                            scroll = !effectivePageView,
                            globalBold = globalEpubTypography.bold,
                            paragraphSpacingPercent = globalParagraphSpacingPercent,
                            lineSpacingStep = globalLineSpacingStep,
                        ),
                    )
                },
                onDismiss = { showEpubTheme = false },
                onPickTextColor = { epubColorTarget = EpubColorTarget.Text },
                onPickPageColor = { epubColorTarget = EpubColorTarget.Page },
                onPickHighlightColor = { epubColorTarget = EpubColorTarget.Highlight },
            )
        }

        when (epubColorTarget) {
            EpubColorTarget.Text ->
                HsvColorPickerDialog(
                    title = "Text color",
                    initialArgb = epubTheme.textArgb,
                    onDismiss = { epubColorTarget = null },
                    onConfirm = { argb ->
                        val next = epubTheme.copy(textArgb = argb)
                        epubTheme = next
                        saveEpubTheme(context, book.id, next)
                        epubNavFragment?.submitPreferences(
                            next.toEpubPreferences(
                                scroll = !effectivePageView,
                                globalBold = globalEpubTypography.bold,
                                paragraphSpacingPercent = globalParagraphSpacingPercent,
                                lineSpacingStep = globalLineSpacingStep,
                            ),
                        )
                        epubColorTarget = null
                    },
                )
            EpubColorTarget.Page ->
                HsvColorPickerDialog(
                    title = "Page color",
                    initialArgb = epubTheme.pageArgb,
                    onDismiss = { epubColorTarget = null },
                    onConfirm = { argb ->
                        val next = epubTheme.copy(pageArgb = argb)
                        epubTheme = next
                        saveEpubTheme(context, book.id, next)
                        epubNavFragment?.submitPreferences(
                            next.toEpubPreferences(
                                scroll = !effectivePageView,
                                globalBold = globalEpubTypography.bold,
                                paragraphSpacingPercent = globalParagraphSpacingPercent,
                                lineSpacingStep = globalLineSpacingStep,
                            ),
                        )
                        epubColorTarget = null
                    },
                )
            EpubColorTarget.Highlight ->
                HsvColorPickerDialog(
                    title = "Highlight color",
                    initialArgb = epubTheme.highlightArgb,
                    onDismiss = { epubColorTarget = null },
                    onConfirm = { argb ->
                        val next = epubTheme.copy(highlightArgb = argb)
                        epubTheme = next
                        saveEpubTheme(context, book.id, next)
                        epubColorTarget = null
                    },
                )
            null -> Unit
        }

        if (showBookSettings) {
            AlertDialog(
                onDismissRequest = { showBookSettings = false },
                confirmButton = { TextButton(onClick = { showBookSettings = false }) { Text("Done") } },
                dismissButton = {
                    TextButton(onClick = {
                        bookPageViewOverride = null
                        context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE)
                            .edit().remove("book_page_view_override_epub_${book.id}").apply()
                    }) { Text("Use global default") }
                },
                title = { Text("Book settings") },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        EpubReaderLayoutModeToggle(
                            pageView = (bookPageViewOverride ?: globalPageView),
                            onPageViewChange = { page ->
                                bookPageViewOverride = page
                                context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE)
                                    .edit().putBoolean("book_page_view_override_epub_${book.id}", page).apply()
                            },
                        )
                        Text(
                            text = "Book settings override global defaults for matching options.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        ReaderControlsSettingsSection(
                            settings = readerControls,
                            onSettingsChange = { next ->
                                readerControls = next
                                saveBookReaderControlsSettings(
                                    context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE),
                                    book.id,
                                    next,
                                )
                            },
                        )
                        TextButton(
                            onClick = {
                                val prefs = context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE)
                                clearBookReaderControlsSettings(prefs, book.id)
                                readerControls = loadBookReaderControlsSettings(prefs, book.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Use global reader controls")
                        }
                    }
                },
            )
        }
        when (val s = dictionaryDialogState) {
            is DictionaryDialogState.Definitions -> {
                AlertDialog(
                    onDismissRequest = { dictionaryDialogState = null },
                    confirmButton = {
                        TextButton(onClick = { dictionaryDialogState = null }) { Text("Close") }
                    },
                    title = { Text("Dictionary: ${s.lemma}") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            s.definitions.forEachIndexed { index, def ->
                                Text("${index + 1}. $def")
                                if (index != s.definitions.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                        }
                    },
                )
            }
            is DictionaryDialogState.NotFound -> {
                var userDefInput by remember(s.lemma) { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { dictionaryDialogState = null },
                    title = { Text("Dictionary: ${s.lemma}") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text("Word not found in dictionary.")
                            Text(
                                "If you want, type to add a definition below.",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedTextField(
                                value = userDefInput,
                                onValueChange = { userDefInput = it },
                                label = { Text("Your definition") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                minLines = 2,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val t = userDefInput.trim()
                                if (t.isBlank()) return@TextButton
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        userDictDao.upsert(UserDictionaryEntity(word = s.lemma, definition = t))
                                    }
                                    dictionaryDialogState = DictionaryDialogState.Definitions(s.lemma, listOf(t))
                                }
                            },
                            enabled = userDefInput.trim().isNotBlank(),
                        ) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dictionaryDialogState = null }) { Text("Close") }
                    },
                )
            }
            null -> Unit
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

private fun fileNameFromPath(path: String): String = File(path).nameWithoutExtension.ifBlank { "Book" }

private fun normalizeBookHref(href: String): String =
    href.substringBefore('#').substringBefore('?').trim().lowercase()

private fun resolveSpineIndexForHref(spineUrls: List<String>, rawHref: String): Int {
    val target = normalizeBookHref(rawHref)
    if (target.isBlank()) return -1
    val exact = spineUrls.indexOfFirst { normalizeBookHref(it) == target }
    if (exact >= 0) return exact
    return spineUrls.indexOfFirst {
        val s = normalizeBookHref(it)
        s.endsWith(target) || target.endsWith(s)
    }
}

private fun extractTocEntries(publication: Publication): List<TocEntry> {
    fun walk(links: List<Link>, out: MutableList<TocEntry>) {
        links.forEach { link ->
            val title = link.title?.trim().orEmpty()
            val href = link.href.toString()
            if (title.isNotBlank() && href.isNotBlank()) {
                out.add(TocEntry(title = title, href = href))
            }
            if (link.children.isNotEmpty()) {
                walk(link.children, out)
            }
        }
    }
    val out = mutableListOf<TocEntry>()
    walk(publication.tableOfContents, out)
    return out.distinctBy { it.href.substringBefore('#').substringBefore('?').lowercase() }
}

private fun detectFirstMainChapterIndex(spineUrls: List<String>, toc: List<TocEntry>): Int {
    if (spineUrls.isEmpty()) return 0

    val chapterOneTitleRegex = Regex("""(?i)\b(chapter|ch\.?)\s*0*1\b""")
    val chapterOnePathRegex = Regex("""(?i)\b(ch(?:apter)?)?[_\-\s]*0*1\b""")
    val frontMatterRegex = Regex(
        """(?i)\b(contents?|toc|foreword|preface|introduction|prologue|copyright|title|cover|acknowledg(e)?ments?)\b""",
    )

    fun spineIndexForHref(rawHref: String): Int {
        return resolveSpineIndexForHref(spineUrls, rawHref)
    }

    val tocMainIdx = toc.firstOrNull { entry ->
        chapterOneTitleRegex.containsMatchIn(entry.title) ||
            chapterOnePathRegex.containsMatchIn(normalizeBookHref(entry.href).substringAfterLast('/'))
    }?.let { spineIndexForHref(it.href) } ?: -1
    if (tocMainIdx >= 0) return tocMainIdx

    val pathMainIdx = spineUrls.indexOfFirst { href ->
        val file = normalizeBookHref(href).substringAfterLast('/')
        chapterOnePathRegex.containsMatchIn(file)
    }
    if (pathMainIdx >= 0) return pathMainIdx

    val firstNonFrontMatter = spineUrls.indexOfFirst { href ->
        val file = normalizeBookHref(href).substringAfterLast('/')
        !frontMatterRegex.containsMatchIn(file)
    }
    return if (firstNonFrontMatter >= 0) firstNonFrontMatter else 0
}

private fun toRoman(n: Int): String {
    if (n <= 0) return "I"
    val vals = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD",
        100 to "C", 90 to "XC", 50 to "L", 40 to "XL",
        10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )
    var x = n
    val sb = StringBuilder()
    for ((v, r) in vals) {
        while (x >= v) {
            sb.append(r)
            x -= v
        }
    }
    return sb.toString()
}

private fun formatChapterLabel(spineIndex: Int, firstMainChapterIndex: Int): String {
    val i = spineIndex.coerceAtLeast(0)
    return if (i < firstMainChapterIndex) {
        "Chapter ${toRoman(i + 1)}"
    } else {
        "Chapter ${i - firstMainChapterIndex + 1}"
    }
}

/**
 * Maps the navigator’s current [Locator] to an index in the publication [positions] list.
 */
private fun indexOfLocatorInPublicationPositions(
    positions: List<Locator>,
    current: Locator,
): Int {
    if (positions.isEmpty()) return 0
    val wanted = current.locations.position
    if (wanted != null && wanted >= 1) {
        val idx = wanted - 1
        if (idx in positions.indices && positions[idx].locations.position == wanted) {
            return idx
        }
    }
    val href = current.href
    val prog = current.locations.progression
    val sameHref = positions.mapIndexed { i, loc -> i to loc }.filter { it.second.href == href }
    if (sameHref.isEmpty()) return 0
    if (sameHref.size == 1) return sameHref.first().first
    if (prog != null) {
        val best = sameHref.minByOrNull { (_, loc) ->
            val p = loc.locations.progression
            if (p == null) Double.MAX_VALUE else abs(p - prog)
        }
        if (best != null) return best.first
    }
    return sameHref.first().first
}

private suspend fun readSelectionText(fragment: EpubNavigatorFragment?): String {
    val raw = fragment?.evaluateJavascript(
        """
        (function() {
          try {
            var s = window.getSelection ? window.getSelection().toString() : "";
            return JSON.stringify((s || "").trim());
          } catch (e) {
            return JSON.stringify("");
          }
        })();
        """.trimIndent(),
    ) ?: "\"\""
    return decodeJsStringResult(raw)
}

private fun decodeJsStringResult(raw: String): String {
    val s = raw.trim()
    if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
        return s.substring(1, s.length - 1)
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim()
    }
    return s.trim()
}

private sealed class DictionaryDialogState {
    data class Definitions(val lemma: String, val definitions: List<String>) : DictionaryDialogState()
    data class NotFound(val lemma: String) : DictionaryDialogState()
}

private sealed class OfflineDictResult {
    data class Ok(val definitions: List<String>, val lemma: String) : OfflineDictResult()
    data class MissingDb(val detail: String) : OfflineDictResult()
    data class NoEntry(val lemma: String) : OfflineDictResult()
    data class DbError(val message: String) : OfflineDictResult()
}

private suspend fun lookupOfflineWordnetDefinitions(
    context: android.content.Context,
    term: String,
    userDao: UserDictionaryDao,
): OfflineDictResult = withContext(Dispatchers.IO) {
    val q = normalizeWordnetLookupTerm(term)
    if (q.isBlank()) return@withContext OfflineDictResult.NoEntry("")
    val dbFile = ensureWordnetDbPresent(context)
        ?: return@withContext OfflineDictResult.MissingDb("could not copy from assets")
    val wordnetDefs = runCatching {
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            queryWordnetDefinitions(db, q)
        }
    }
    when {
        wordnetDefs.isFailure -> {
            val e = wordnetDefs.exceptionOrNull()
            Log.w(TAG_WORDNET, "open/query failed: ${dbFile.absolutePath}", e)
            OfflineDictResult.DbError(e?.message ?: "open failed")
        }
        wordnetDefs.getOrNull()?.isNotEmpty() == true -> OfflineDictResult.Ok(wordnetDefs.getOrThrow(), q)
        else -> {
            val userRow = userDao.getByWord(q)
            if (userRow != null) {
                OfflineDictResult.Ok(listOf(userRow.definition), q)
            } else {
                OfflineDictResult.NoEntry(q)
            }
        }
    }
}

/** Bundled OEWN is ~160MB; [AssetManager.list] often omits large files, so open by name only. */
private fun ensureWordnetDbPresent(context: android.content.Context): File? {
    val dir = File(context.filesDir, "dictionary").apply { mkdirs() }
    val target = File(dir, "oewn.sqlite")
    val minBytes = 1_000_000L

    fun isReadableWordnet(path: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(path.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('words','senses','synsets')",
                null,
            ).use { c ->
                c.moveToFirst() && c.getInt(0) >= 3
            }
        }
    }.getOrDefault(false)

    if (target.exists() && target.length() >= minBytes && isReadableWordnet(target)) {
        return target
    }
    if (target.exists()) {
        Log.w(TAG_WORDNET, "Removing invalid or partial DB (${target.length()} bytes)")
        target.delete()
    }

    val assetNames = listOf("oewn.sqlite", "wordnet.sqlite", "wordnet.db", "oewn.db")
    for (name in assetNames) {
        val copied = runCatching {
            context.assets.open(name).use { input ->
                FileOutputStream(target).use { out -> input.copyTo(out) }
            }
            true
        }.getOrDefault(false)
        if (!copied) continue
        if (target.length() < minBytes) {
            Log.w(TAG_WORDNET, "Asset $name too small (${target.length()} bytes)")
            target.delete()
            continue
        }
        if (!isReadableWordnet(target)) {
            Log.w(TAG_WORDNET, "Asset $name is not a valid OEWN sqlite")
            target.delete()
            continue
        }
        Log.d(TAG_WORDNET, "Installed WordNet DB from assets/$name (${target.length()} bytes)")
        return target
    }
    Log.e(TAG_WORDNET, "No usable WordNet asset (tried ${assetNames.joinToString()})")
    return null
}

private const val TAG_WORDNET = "OptireaderWordnet"

/**
 * Selection text from the WebView often includes trailing punctuation (`emotion.`), smart quotes,
 * or invisible Unicode (ZWSP). WordNet lemmas are single tokens without such noise.
 */
private fun normalizeWordnetLookupTerm(raw: String): String {
    var s = raw
        .replace("\u200B", "")
        .replace("\u200C", "")
        .replace("\u200D", "")
        .replace("\uFEFF", "")
        .trim()
    if (s.isEmpty()) return ""
    s = s.split(Regex("\\s+")).firstOrNull()?.trim() ?: ""
    val start = s.indexOfFirst { it.isLetter() }
    val end = s.indexOfLast { it.isLetter() }
    if (start < 0 || end < start) return ""
    return s.substring(start, end + 1).lowercase(Locale.US)
}

private fun queryWordnetDefinitions(db: SQLiteDatabase, q: String): List<String> {
    val out = linkedSetOf<String>()
    val queries = listOf(
        // OEWN sqlite schema (confirmed in bundled DB): words.word + senses + synsets.
        """
        SELECT DISTINCT sy.definition
        FROM words w
        JOIN senses se ON se.wordid = w.wordid
        JOIN synsets sy ON sy.synsetid = se.synsetid
        WHERE lower(w.word) = ?
        LIMIT 12
        """.trimIndent(),
        """
        SELECT DISTINCT sy.definition
        FROM casedwords cw
        JOIN senses se ON se.casedwordid = cw.casedwordid
        JOIN synsets sy ON sy.synsetid = se.synsetid
        WHERE lower(cw.casedword) = ?
        LIMIT 12
        """.trimIndent(),
        """
        SELECT DISTINCT definition
        FROM synsets
        WHERE synsetid IN (
            SELECT se.synsetid
            FROM senses se
            JOIN words w ON w.wordid = se.wordid
            WHERE lower(w.word) = ?
        )
        LIMIT 12
        """.trimIndent(),
    )
    queries.forEach { sql ->
        runCatching {
            db.rawQuery(sql, arrayOf(q)).use { c ->
                while (c.moveToNext()) {
                    c.getString(0)?.trim()?.takeIf { it.isNotBlank() }?.let(out::add)
                }
            }
        }
    }
    return out.toList()
}

private const val KEY_EPUB_HIGHLIGHTS_PREFIX = "epub_highlights_"

private fun savePersistedHighlights(context: android.content.Context, bookId: Long, highlights: List<Decoration>) {
    val arr = JSONArray()
    highlights.forEach { deco ->
        arr.put(
            JSONObject().apply {
                put("id", deco.id)
                put("locator", deco.locator.toJSON())
                put(
                    "tint",
                    (deco.style as? Decoration.Style.Highlight)?.tint
                        ?: 0xFFFFF59D.toInt(),
                )
            },
        )
    }
    context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE)
        .edit()
        .putString("$KEY_EPUB_HIGHLIGHTS_PREFIX$bookId", arr.toString())
        .apply()
}

private fun loadPersistedHighlights(context: android.content.Context, bookId: Long): List<Decoration> {
    val raw = context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE)
        .getString("$KEY_EPUB_HIGHLIGHTS_PREFIX$bookId", null)
        ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val loc = Locator.fromJSON(o.optJSONObject("locator")) ?: continue
                add(
                    Decoration(
                        id = o.optString("id").ifBlank { "hl_${System.currentTimeMillis()}_$i" },
                        locator = loc,
                        style = Decoration.Style.Highlight(
                            tint = if (o.has("tint")) o.optInt("tint") else 0xFFFFF59D.toInt(),
                        ),
                    ),
                )
            }
        }
    }.getOrElse { emptyList() }
}

private enum class EpubColorTarget { Text, Page, Highlight }

private data class EpubThemeState(
    val fontFamilyName: String,
    val fontSizePercent: Double,
    val textArgb: Int,
    val pageArgb: Int,
    val highlightArgb: Int,
) {
    fun toEpubPreferences(
        scroll: Boolean,
        globalBold: Boolean,
        paragraphSpacingPercent: Int,
        lineSpacingStep: Int,
    ): EpubPreferences =
        EpubPreferences(
            scroll = scroll,
            fontFamily = FontFamily(fontFamilyName),
            fontSize = fontSizePercent,
            fontWeight = if (globalBold) 1.85 else 1.0,
            lineHeight = lineSpacingStepToReadiumLineHeight(lineSpacingStep),
            paragraphSpacing = paragraphSpacingPercentToReadium(paragraphSpacingPercent),
            pageMargins = 1.5,
            textColor = ReadiumColor(textArgb),
            backgroundColor = ReadiumColor(pageArgb),
            publisherStyles = false,
        )

    companion object {
        /** Fallback when app theme cannot be read; matches day mode reader defaults. */
        fun default() = EpubThemeState(
            fontFamilyName = FontFamily.SANS_SERIF.name,
            fontSizePercent = 1.0,
            textArgb = 0xFF000000.toInt(),
            pageArgb = 0xFFAFF8FF.toInt(),
            highlightArgb = 0xFFFFF59D.toInt(),
        )
    }
}

private fun cssColorToArgb(css: String, fallback: Int): Int =
    runCatching { Color.parseColor(css.trim()) }.getOrElse { fallback }

private fun loadEpubTheme(context: android.content.Context, bookId: Long, style: EpubPageStyle): EpubThemeState {
    val prefs = context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE)
    val baseline = resolveEpubThemeBaseline(prefs, style)
    val font = prefs.getString("epub_theme_font_$bookId", null) ?: baseline.fontFamilyName
    val size = prefs.getFloat("epub_theme_font_size_$bookId", baseline.fontSizePercent.toFloat())
        .toDouble().coerceIn(0.1, 5.0)
    val text = if (prefs.contains("epub_theme_text_$bookId")) {
        prefs.getInt("epub_theme_text_$bookId", baseline.textArgb)
    } else {
        baseline.textArgb
    }
    val page = if (prefs.contains("epub_theme_page_$bookId")) {
        prefs.getInt("epub_theme_page_$bookId", baseline.pageArgb)
    } else {
        baseline.pageArgb
    }
    val highlight = if (prefs.contains("epub_theme_highlight_$bookId")) {
        prefs.getInt("epub_theme_highlight_$bookId", baseline.highlightArgb)
    } else {
        baseline.highlightArgb
    }
    return EpubThemeState(font, size, text, page, highlight)
}

private fun saveEpubTheme(context: android.content.Context, bookId: Long, state: EpubThemeState) {
    context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE).edit()
        .putString("epub_theme_font_$bookId", state.fontFamilyName)
        .putFloat("epub_theme_font_size_$bookId", state.fontSizePercent.toFloat())
        .putInt("epub_theme_text_$bookId", state.textArgb)
        .putInt("epub_theme_page_$bookId", state.pageArgb)
        .putInt("epub_theme_highlight_$bookId", state.highlightArgb)
        .apply()
}

@Composable
private fun EpubReaderThemeSheet(
    bookId: Long,
    theme: EpubThemeState,
    onThemeChange: (EpubThemeState) -> Unit,
    onDismiss: () -> Unit,
    onPickTextColor: () -> Unit,
    onPickPageColor: () -> Unit,
    onPickHighlightColor: () -> Unit,
) {
    val context = LocalContext.current
    val readerOptionsPrefs = remember {
        context.getSharedPreferences("reader_options", android.content.Context.MODE_PRIVATE)
    }
    var expanded by remember(bookId) { mutableStateOf(false) }
    var fontNames by remember(bookId) { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(bookId) {
        fontNames = withContext(Dispatchers.Default) { SystemFontFamilies.loadCssFontFamilyNames() }
    }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Theme") },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = theme.fontFamilyName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Font") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = fontNames.isNotEmpty()) { expanded = true },
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                    ) {
                        LazyColumn {
                            items(fontNames) { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        expanded = false
                                        onThemeChange(theme.copy(fontFamilyName = name))
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    "Font size (${(theme.fontSizePercent * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Slider(
                    value = theme.fontSizePercent.toFloat(),
                    onValueChange = { v ->
                        onThemeChange(theme.copy(fontSizePercent = v.toDouble().coerceIn(0.1, 5.0)))
                    },
                    valueRange = 0.5f..2f,
                )
                ThemeColorSwatch(
                    colorArgb = theme.textArgb,
                    label = "Text color",
                    onClick = onPickTextColor,
                )
                ThemeColorSwatch(
                    colorArgb = theme.pageArgb,
                    label = "Page color",
                    onClick = onPickPageColor,
                )
                ThemeColorSwatch(
                    colorArgb = theme.highlightArgb,
                    label = "Highlight color",
                    onClick = onPickHighlightColor,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                ReaderGlobalThemePreferences(readerOptionsPrefs = readerOptionsPrefs)
            }
        },
    )
}

private fun estimateWordAndCharCountForEpub(file: File): Pair<Int, Int> = runCatching {
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
