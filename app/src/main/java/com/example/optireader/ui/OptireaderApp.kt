package com.example.optireader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import android.app.Activity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.optireader.ImportUiState
import com.example.optireader.OptireaderViewModel
import com.example.optireader.R
import com.example.optireader.data.BookEntity
import com.example.optireader.data.ShelfItem
import com.example.optireader.data.preferences.AppPreferences
import com.example.optireader.data.preferences.PreferencesRepository
import com.example.optireader.scan.DiscoveredSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private object Routes {
    /** Pager: menu (0), library (1), TBR (2), import (3). */
    const val Home = "home"
    const val Reader = "reader/{bookId}"
    const val Folder = "folder/{folderId}"
    fun reader(bookId: Long) = "reader/$bookId"
    fun folder(folderId: Long) = "folder/$folderId"
}

private fun readerRouteBookId(route: String?): Long? {
    if (route == null || !route.startsWith("reader/")) return null
    return route.removePrefix("reader/").substringBefore('?').toLongOrNull()
}

private object MainTab {
    const val Menu = 0
    const val Library = 1
    const val Tbr = 2
    const val Import = 3
}

private val BottomMenuBarHeight = 80.dp
private val TopPanelHeight = 51.dp // ~30% smaller than 73.dp.

private enum class FinishFilter { Any, Finished, Unfinished }
private enum class FormatFilter { Any, Epub, Pdf }
private enum class TbrStartedFilter { Any, Started, NotStarted }
private enum class TbrSortMode { ImportNewest, ImportOldest, FileNameAsc, FileNameDesc }

private data class LibraryFilterState(
    val minReads: Int? = null,
    val ratingMin: Float? = null,
    val ratingMax: Float? = null,
    val finishFilter: FinishFilter = FinishFilter.Any,
    val formatFilter: FormatFilter = FormatFilter.Any,
)

private data class TbrFilterState(
    val sortMode: TbrSortMode = TbrSortMode.ImportOldest,
    val startedFilter: TbrStartedFilter = TbrStartedFilter.Any,
)

private fun applyLibraryFilters(
    items: List<ShelfItem>,
    filters: LibraryFilterState,
    readSessionCounts: Map<Long, Int>,
): List<ShelfItem> {
    fun matches(book: BookEntity): Boolean {
        val reads = readSessionCounts[book.id] ?: 0
        if (filters.minReads != null && reads < filters.minReads) return false
        val rating = book.rating10
        if (filters.ratingMin != null && (rating == null || rating < filters.ratingMin)) return false
        if (filters.ratingMax != null && (rating == null || rating > filters.ratingMax)) return false
        when (filters.finishFilter) {
            FinishFilter.Finished -> if (book.readProgress01 < 0.999f) return false
            FinishFilter.Unfinished -> if (book.readProgress01 >= 0.999f) return false
            FinishFilter.Any -> Unit
        }
        when (filters.formatFilter) {
            FormatFilter.Epub -> if (book.format.lowercase() != "epub") return false
            FormatFilter.Pdf -> if (book.format.lowercase() != "pdf") return false
            FormatFilter.Any -> Unit
        }
        return true
    }
    return items.mapNotNull { item ->
        when (item) {
            is ShelfItem.BookTile -> if (matches(item.book)) item else null
            is ShelfItem.FolderTile -> {
                val filteredBooks = item.books.filter(::matches)
                if (filteredBooks.isNotEmpty()) item.copy(books = filteredBooks) else null
            }
        }
    }
}

private fun applyTbrFilters(
    items: List<ShelfItem>,
    filters: TbrFilterState,
): List<ShelfItem> {
    val books = items.mapNotNull { (it as? ShelfItem.BookTile)?.book }
        .filter { book ->
            when (filters.startedFilter) {
                TbrStartedFilter.Started -> book.readProgress01 > 0f
                TbrStartedFilter.NotStarted -> book.readProgress01 <= 0f
                TbrStartedFilter.Any -> true
            }
        }
    val sorted = when (filters.sortMode) {
        TbrSortMode.ImportNewest -> books.sortedByDescending { it.importedAtMillis }
        TbrSortMode.ImportOldest -> books.sortedBy { it.importedAtMillis }
        TbrSortMode.FileNameAsc -> books.sortedBy { File(it.localPath).name.lowercase() }
        TbrSortMode.FileNameDesc -> books.sortedByDescending { File(it.localPath).name.lowercase() }
    }
    return sorted.map { ShelfItem.BookTile(it) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OptireaderApp(
    viewModel: OptireaderViewModel,
    preferencesRepository: PreferencesRepository,
    prefs: AppPreferences,
    onScanDownloads: () -> Unit,
    onExportStatistics: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = if (prefs.followSystemDarkMode) systemDark else !prefs.useDayTheme
    val epubPageStyle = when {
        !isDark ->
            EpubPageStyle(backgroundCss = "#AFF8FF", foregroundCss = "#000000")
        prefs.useAmoledDark && isDark ->
            EpubPageStyle(backgroundCss = "#000000", foregroundCss = "#FFFFFF")
        else ->
            EpubPageStyle(backgroundCss = "#23001D", foregroundCss = "#FFFFFF")
    }

    val navController = rememberNavController()
    fun navigateToReader(bookId: Long) {
        navController.navigate(Routes.reader(bookId)) {
            launchSingleTop = true
        }
    }
    fun navigateToFolder(folderId: Long) {
        navController.navigate(Routes.folder(folderId)) {
            launchSingleTop = true
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val route = navBackStackEntry?.destination?.route
    val showLibraryNav = route == Routes.Home || route?.startsWith("folder/") == true
    val isReaderRoute = route?.startsWith("reader/") == true
    val layoutDirection = LocalLayoutDirection.current

    var mainTabIndex by rememberSaveable { mutableIntStateOf(MainTab.Library) }
    val pagerState = rememberPagerState(
        initialPage = mainTabIndex,
        pageCount = { 4 },
    )
    LaunchedEffect(mainTabIndex) {
        if (pagerState.currentPage != mainTabIndex) {
            pagerState.animateScrollToPage(mainTabIndex)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                if (mainTabIndex != page) {
                    mainTabIndex = page
                }
            }
    }

    val shelfItems by viewModel.shelfItems.collectAsState(initial = emptyList())
    val tbrItems by viewModel.tbrItems.collectAsState(initial = emptyList())
    val import by viewModel.import.collectAsState(initial = ImportUiState())
    val context = LocalContext.current
    var libraryActionBookId by remember { mutableStateOf<Long?>(null) }
    var libraryStatsContext by remember { mutableStateOf<LibraryBookStatsContext?>(null) }
    var libraryOverlayMode by remember { mutableStateOf<LibraryOverlayMode?>(null) }
    var confirmMoveToTbrBookId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteBookId by remember { mutableStateOf<Long?>(null) }
    var confirmRemoveFromFolderBookId by remember { mutableStateOf<Long?>(null) }
    var recommendationBooks by remember { mutableStateOf<List<BookEntity>?>(null) }
    var recommendationsLoading by remember { mutableStateOf(false) }
    var showLibraryFilterDialog by remember { mutableStateOf(false) }
    var showTbrFilterDialog by remember { mutableStateOf(false) }
    var libraryFilters by remember { mutableStateOf(LibraryFilterState()) }
    var tbrFilters by remember { mutableStateOf(TbrFilterState()) }
    val readSessionCounts = remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }

    LaunchedEffect(route, mainTabIndex) {
        val onLibraryTbrOrFolder =
            (route == Routes.Home && (mainTabIndex == MainTab.Library || mainTabIndex == MainTab.Tbr)) ||
                route?.startsWith("folder/") == true
        if (!onLibraryTbrOrFolder) {
            libraryActionBookId = null
            libraryStatsContext = null
            libraryOverlayMode = null
            confirmMoveToTbrBookId = null
            confirmDeleteBookId = null
            recommendationBooks = null
            recommendationsLoading = false
        }
        if (route?.startsWith("folder/") != true) {
            confirmRemoveFromFolderBookId = null
        }
    }

    LaunchedEffect(mainTabIndex) {
        libraryActionBookId = null
        libraryStatsContext = null
        libraryOverlayMode = null
        confirmMoveToTbrBookId = null
        confirmDeleteBookId = null
        recommendationBooks = null
        recommendationsLoading = false
    }

    LaunchedEffect(shelfItems, tbrItems) {
        val allBooks = (shelfItems.flatMap { shelf ->
            when (shelf) {
                is ShelfItem.BookTile -> listOf(shelf.book)
                is ShelfItem.FolderTile -> shelf.books
            }
        } + tbrItems.mapNotNull { (it as? ShelfItem.BookTile)?.book }).distinctBy { it.id }
        val contextMap = allBooks.associate { b ->
            b.id to BookStatsStore.load(context, b.id).readSessions.size
        }
        readSessionCounts.value = contextMap
    }

    val filteredLibraryItems = remember(shelfItems, libraryFilters, readSessionCounts.value) {
        applyLibraryFilters(shelfItems, libraryFilters, readSessionCounts.value)
    }
    val filteredTbrItems = remember(tbrItems, tbrFilters) {
        applyTbrFilters(tbrItems, tbrFilters)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val libraryBookCount = remember(shelfItems) {
        shelfItems.sumOf { item ->
            when (item) {
                is ShelfItem.BookTile -> 1
                is ShelfItem.FolderTile -> item.books.size
            }
        }
    }
    val tbrBookCount = remember(tbrItems) {
        tbrItems.count { it is ShelfItem.BookTile }
    }
    val shouldShowWelcome = libraryBookCount == 0 && tbrBookCount == 0
    var welcomeSplashVisible by remember { mutableStateOf(false) }
    var showGlobalSettingsDialog by remember { mutableStateOf(false) }
    var firstImportWizardSources by remember { mutableStateOf<List<DiscoveredSource>?>(null) }

    val totalImportedBooks = libraryBookCount + tbrBookCount
    /** Until the first batch lands on Library/TBR, onboarding keeps the bottom bar and pager locked. */
    val importNavLocked =
        prefs.pendingFirstImportOnboarding &&
            !prefs.firstImportWizardCompleted &&
            totalImportedBooks == 0

    LaunchedEffect(shouldShowWelcome, route) {
        if (shouldShowWelcome && (route == null || route == Routes.Home)) {
            welcomeSplashVisible = true
        }
    }

    LaunchedEffect(prefs.firstImportWizardCompleted, libraryBookCount, tbrBookCount) {
        if (prefs.firstImportWizardCompleted) return@LaunchedEffect
        if (libraryBookCount + tbrBookCount > 0) {
            preferencesRepository.setFirstImportWizardCompleted(true)
            preferencesRepository.setPendingFirstImportOnboarding(false)
        }
    }

    fun navigateToHomeFromTabs() {
        navController.navigate(Routes.Home) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(import.toast) {
        val t = import.toast ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(t)
        viewModel.consumeToast()
    }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.statisticsExportMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(enabled = confirmMoveToTbrBookId != null) {
        confirmMoveToTbrBookId = null
    }
    BackHandler(enabled = confirmRemoveFromFolderBookId != null) {
        confirmRemoveFromFolderBookId = null
    }
    BackHandler(enabled = confirmDeleteBookId != null) {
        confirmDeleteBookId = null
    }

    fun openLibraryBookInfoFromPanel() {
        val bid = libraryActionBookId ?: return
        scope.launch {
            val book = viewModel.loadBook(bid) ?: return@launch
            libraryStatsContext = quickLibraryBookStatsContext(context, book)
            libraryOverlayMode = LibraryOverlayMode.BookInfo
            libraryActionBookId = null
            launch {
                val full = runCatching {
                    loadLibraryBookStatsContext(context, book)
                }.getOrNull() ?: return@launch
                if (libraryOverlayMode == LibraryOverlayMode.BookInfo &&
                    libraryStatsContext?.book?.id == book.id
                ) {
                    libraryStatsContext = full
                }
            }
        }
    }
    fun openLibraryStatsFromPanel() {
        val bid = libraryActionBookId ?: return
        scope.launch {
            val book = viewModel.loadBook(bid) ?: return@launch
            libraryStatsContext = runCatching {
                loadLibraryBookStatsContext(context, book)
            }.getOrElse {
                LibraryBookStatsContext(
                    book = book,
                    info = BookInfoUiData(
                        chapters = emptyList(),
                        chapterPageIndexBySpine = emptyMap(),
                        title = book.title,
                        author = "Unknown",
                        creator = "Unknown",
                        fileName = java.io.File(book.localPath).name,
                        filePath = book.localPath,
                        fileSizeBytes = java.io.File(book.localPath).length(),
                        totalUnits = 1,
                        currentUnit = 1,
                        wordsCount = 0,
                        charsCount = 0,
                    ),
                    totalReadingSeconds = 0L,
                    historyDays = 1,
                    avgWpmUser = 0f,
                    avgWpmBook = 0f,
                )
            }
            libraryOverlayMode = LibraryOverlayMode.Stats
            libraryActionBookId = null
        }
    }
    fun moveLibraryBookToTbrFromPanel() {
        val bid = libraryActionBookId ?: return
        confirmMoveToTbrBookId = bid
        libraryActionBookId = null
    }
    fun requestDeleteBookFromPanel() {
        val bid = libraryActionBookId ?: return
        confirmDeleteBookId = bid
        libraryActionBookId = null
    }
    fun requestRemoveFromFolderFromPanel() {
        val bid = libraryActionBookId ?: return
        confirmRemoveFromFolderBookId = bid
        libraryActionBookId = null
    }

    val isLibraryTab = route == Routes.Home && mainTabIndex == MainTab.Library
    val isFolderRoute = route?.startsWith("folder/") == true
    val isTbrTab = route == Routes.Home && mainTabIndex == MainTab.Tbr
    val showFilterIcon = (isLibraryTab || isTbrTab) && libraryActionBookId == null
    val tbrActionBookForBar = remember(libraryActionBookId, filteredTbrItems) {
        val id = libraryActionBookId ?: return@remember null
        filteredTbrItems.asSequence()
            .mapNotNull { it as? ShelfItem.BookTile }
            .firstOrNull { it.book.id == id }
            ?.book
    }
    val tbrBarShowStats = remember(tbrActionBookForBar, context) {
        val b = tbrActionBookForBar ?: return@remember false
        tbrBookEligibleForStatsIcon(context, b)
    }

    Box(modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (showLibraryNav) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(TopPanelHeight),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ) {
                        if ((isLibraryTab || isFolderRoute || isTbrTab) && libraryActionBookId != null) {
                            LibraryBookActionBar(
                                onOpenBookInfo = ::openLibraryBookInfoFromPanel,
                                onOpenStats = ::openLibraryStatsFromPanel,
                                onMoveToTbr = ::moveLibraryBookToTbrFromPanel,
                                onRequestDelete = ::requestDeleteBookFromPanel,
                                onDismiss = { libraryActionBookId = null },
                                onRemoveFromFolder = if (isFolderRoute) {
                                    ::requestRemoveFromFolderFromPanel
                                } else {
                                    null
                                },
                                showStatsButton = !isTbrTab || tbrBarShowStats,
                                showMoveToTbrButton = !isTbrTab,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Spacer(Modifier.weight(1f))
                                if (showFilterIcon) {
                                    IconButton(
                                        onClick = {
                                            if (isLibraryTab) {
                                                showLibraryFilterDialog = true
                                            } else if (isTbrTab) {
                                                showTbrFilterDialog = true
                                            }
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.FilterList,
                                            contentDescription = "Filter",
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.width(48.dp))
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (showLibraryNav) {
                    NavigationBar(
                        modifier = Modifier.height(BottomMenuBarHeight),
                        containerColor = when {
                            route == Routes.Home && mainTabIndex == MainTab.Import ->
                                MaterialTheme.colorScheme.surface
                            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        },
                    ) {
                        NavigationBarItem(
                            enabled = !importNavLocked,
                            selected = route == Routes.Home && mainTabIndex == MainTab.Menu,
                            onClick = {
                                mainTabIndex = MainTab.Menu
                                navigateToHomeFromTabs()
                            },
                            icon = {
                                Icon(
                                    Icons.Filled.Menu,
                                    contentDescription = stringResource(R.string.nav_menu),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_menu)) },
                        )
                        NavigationBarItem(
                            enabled = !importNavLocked,
                            selected =
                                (route == Routes.Home && mainTabIndex == MainTab.Library) ||
                                    route?.startsWith("folder/") == true,
                            onClick = {
                                mainTabIndex = MainTab.Library
                                navigateToHomeFromTabs()
                            },
                            icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_library)) },
                        )
                        NavigationBarItem(
                            enabled = !importNavLocked,
                            selected = route == Routes.Home && mainTabIndex == MainTab.Tbr,
                            onClick = {
                                mainTabIndex = MainTab.Tbr
                                navigateToHomeFromTabs()
                            },
                            icon = { Icon(Icons.Outlined.Bookmarks, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_tbr)) },
                        )
                        NavigationBarItem(
                            enabled = !importNavLocked,
                            selected = route == Routes.Home && mainTabIndex == MainTab.Import,
                            onClick = {
                                mainTabIndex = MainTab.Import
                                navigateToHomeFromTabs()
                            },
                            icon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_import)) },
                        )
                    }
                }
            },
        ) { padding ->
            val navPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = if (isReaderRoute) 0.dp else padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding(),
            )
            Box(Modifier.fillMaxSize()) {
                val recommendationsLoadingInteraction = remember { MutableInteractionSource() }
                NavHost(
                    navController = navController,
                    startDestination = Routes.Home,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(navPadding),
                ) {
                composable(Routes.Home) {
                    Box(Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = !importNavLocked,
                        ) { page ->
                            when (page) {
                            MainTab.Menu -> {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background,
                                ) {
                                    AppDrawerContent(
                                        prefs = prefs,
                                        preferencesRepository = preferencesRepository,
                                        epubPageStyle = epubPageStyle,
                                        onExportStatistics = onExportStatistics,
                                        showGlobalSettingsDialog = showGlobalSettingsDialog,
                                        onShowGlobalSettingsDialogChange = { showGlobalSettingsDialog = it },
                                        onGlobalSettingsConfirmed = {
                                            mainTabIndex = MainTab.Import
                                            navigateToHomeFromTabs()
                                        },
                                        onGlobalSettingsDismissed = {
                                            scope.launch {
                                                preferencesRepository.setPendingFirstImportOnboarding(false)
                                            }
                                        },
                                    )
                                }
                            }
                            MainTab.Library -> {
                                LibraryMainTabWithOverlays(
                                    shelfItems = filteredLibraryItems,
                                    showReadingProgress = prefs.showReadingProgress,
                                    isDayTheme = !isDark,
                                    useAmoledDark = prefs.useAmoledDark && isDark,
                                    onNavigateToReader = ::navigateToReader,
                                    onNavigateToFolder = ::navigateToFolder,
                                    viewModel = viewModel,
                                    onBookLongPress = { libraryActionBookId = it },
                                )
                            }
                            MainTab.Tbr -> {
                                LibraryScreen(
                                    shelfItems = filteredTbrItems,
                                    showReadingProgress = prefs.showReadingProgress,
                                    isDayTheme = !isDark,
                                    useAmoledDark = prefs.useAmoledDark && isDark,
                                    onBookClick = ::navigateToReader,
                                    onOpenFolder = { },
                                    onShelfOrderCommitted = { },
                                    onMergeBookOntoTarget = { _, _ -> },
                                    dragAndMergeEnabled = false,
                                    onBookLongPress = { libraryActionBookId = it },
                                    emptyMessageResId = R.string.library_tbr_empty,
                                )
                            }
                            MainTab.Import -> {
                                ImportScreen(
                                    state = import,
                                    onQueryChange = viewModel::setQuery,
                                    onToggle = viewModel::toggle,
                                    onSelectAll = viewModel::selectAllVisible,
                                    onSelectNone = viewModel::selectNone,
                                    onImport = {
                                        val selected = viewModel.getSelectedSourcesForImport()
                                        if (selected.isEmpty()) {
                                            viewModel.importSelected()
                                            return@ImportScreen
                                        }
                                        val useFirstImportWizard =
                                            !prefs.firstImportWizardCompleted &&
                                                libraryBookCount == 0 &&
                                                tbrBookCount == 0
                                        if (useFirstImportWizard) {
                                            firstImportWizardSources = selected
                                        } else if (viewModel.importSelected()) {
                                            mainTabIndex = MainTab.Tbr
                                        }
                                    },
                                    onScanDownloads = onScanDownloads,
                                )
                            }
                            else -> {
                                Box(Modifier.fillMaxSize())
                            }
                        }
                        }
                        ShelfLibraryOrTbrBookOverlays(
                            visible = mainTabIndex == MainTab.Library || mainTabIndex == MainTab.Tbr,
                            libraryStatsContext = libraryStatsContext,
                            libraryOverlayMode = libraryOverlayMode,
                            onLibraryStatsContextChange = { libraryStatsContext = it },
                            onLibraryOverlayModeChange = { libraryOverlayMode = it },
                            viewModel = viewModel,
                        )
                    }
                }
                composable(
                    Routes.Reader,
                    arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("bookId") ?: return@composable
                    ReaderRoute(
                        bookId = id,
                        loadBook = { viewModel.loadBook(it) },
                        beginRereadFromComplete = { viewModel.beginRereadFromComplete(it) },
                        epubPageStyle = epubPageStyle,
                        onShelfProgressWhenBookLoaded = { p ->
                            viewModel.setReaderEntryProgressSnapshot(id, p)
                        },
                        onReadingProgress = { fraction ->
                            viewModel.updateReadingProgress(id, fraction)
                        },
                        onRatingChanged = { rating ->
                            viewModel.updateBookRating(id, rating)
                        },
                        onBack = {
                            if (prefs.readerPowerButtonClosesApp) {
                                (context as? Activity)?.finishAffinity()
                            } else {
                                val entrySnapshot = viewModel.takeReaderEntryProgressSnapshot(id) ?: -1f
                                mainTabIndex = MainTab.Library
                                val popped = navController.popBackStack(
                                    route = Routes.Home,
                                    inclusive = false,
                                    saveState = false,
                                )
                                if (!popped) {
                                    navigateToHomeFromTabs()
                                }
                                if (prefs.recommendationsEnabled) {
                                    recommendationsLoading = true
                                    scope.launch {
                                        try {
                                            delay(220)
                                            val after = viewModel.loadBook(id) ?: return@launch
                                            val now = after.readProgress01
                                            val shouldSuggest =
                                                now >= 0.99f &&
                                                    entrySnapshot >= 0f &&
                                                    entrySnapshot < 0.999f
                                            if (!shouldSuggest) return@launch
                                            val recs = viewModel.loadTbrRecommendationsAfterComplete(id)
                                            if (recs.isNotEmpty()) {
                                                recommendationBooks = recs
                                            }
                                        } finally {
                                            recommendationsLoading = false
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
                composable(
                    Routes.Folder,
                    arguments = listOf(navArgument("folderId") { type = NavType.LongType }),
                ) { entry ->
                    val folderId = entry.arguments?.getLong("folderId") ?: return@composable
                    FolderContentsScreen(
                        folderId = folderId,
                        folderFlow = viewModel.observeFolder(folderId),
                        booksFlow = viewModel.folderBooks(folderId),
                        showReadingProgress = prefs.showReadingProgress,
                        isDayTheme = !isDark,
                        useAmoledDark = prefs.useAmoledDark && isDark,
                        onBookClick = ::navigateToReader,
                        onBack = { navController.popBackStack() },
                        onRenameFolder = viewModel::renameFolder,
                        onShelfOrderInFolderCommitted = { orderedKeys ->
                            viewModel.persistFolderBookOrder(folderId, orderedKeys)
                        },
                        onBookLongPress = { libraryActionBookId = it },
                    )
                }
            }
            if (recommendationsLoading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = recommendationsLoadingInteraction,
                            indication = null,
                            onClick = {},
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.recommendation_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (showLibraryFilterDialog) {
                LibraryFilterDialog(
                    initial = libraryFilters,
                    onDismiss = { showLibraryFilterDialog = false },
                    onApply = {
                        libraryFilters = it
                        showLibraryFilterDialog = false
                    },
                )
            }
            if (showTbrFilterDialog) {
                TbrFilterDialog(
                    initial = tbrFilters,
                    onDismiss = { showTbrFilterDialog = false },
                    onApply = {
                        tbrFilters = it
                        showTbrFilterDialog = false
                    },
                )
            }
            confirmMoveToTbrBookId?.let { bookId ->
                AlertDialog(
                    onDismissRequest = { confirmMoveToTbrBookId = null },
                    title = { Text(stringResource(R.string.library_move_tbr_confirm_title)) },
                    text = { Text(stringResource(R.string.library_move_tbr_confirm_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.moveBookToTbr(bookId)
                                confirmMoveToTbrBookId = null
                            },
                        ) {
                            Text(stringResource(R.string.library_move_tbr_confirm_move))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmMoveToTbrBookId = null }) {
                            Text(stringResource(R.string.library_move_tbr_confirm_cancel))
                        }
                    },
                )
            }
            confirmRemoveFromFolderBookId?.let { bookId ->
                AlertDialog(
                    onDismissRequest = { confirmRemoveFromFolderBookId = null },
                    title = { Text(stringResource(R.string.folder_remove_book_confirm_title)) },
                    text = { Text(stringResource(R.string.folder_remove_book_confirm_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.removeBookFromFolderToMainShelf(bookId)
                                confirmRemoveFromFolderBookId = null
                            },
                        ) {
                            Text(stringResource(R.string.folder_remove_book_confirm_remove))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmRemoveFromFolderBookId = null }) {
                            Text(stringResource(R.string.folder_remove_book_confirm_cancel))
                        }
                    },
                )
            }
            confirmDeleteBookId?.let { bookId ->
                AlertDialog(
                    onDismissRequest = { confirmDeleteBookId = null },
                    title = { Text(stringResource(R.string.library_delete_confirm_title)) },
                    text = { Text(stringResource(R.string.library_delete_confirm_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteBookPermanentlyKeepingStats(bookId)
                                confirmDeleteBookId = null
                                if (libraryStatsContext?.book?.id == bookId) {
                                    libraryStatsContext = null
                                    libraryOverlayMode = null
                                }
                                val rid = readerRouteBookId(route)
                                if (rid == bookId) {
                                    navController.popBackStack()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Text(stringResource(R.string.library_delete_confirm_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDeleteBookId = null }) {
                            Text(stringResource(R.string.library_delete_confirm_cancel))
                        }
                    },
                )
            }
            recommendationBooks?.let { books ->
                if (books.isNotEmpty()) {
                    TbrRecommendationDialog(
                        books = books,
                        onSelectBook = { bid ->
                            recommendationBooks = null
                            navigateToReader(bid)
                        },
                        onDecline = { recommendationBooks = null },
                    )
                }
            }
            }
        }
        if (welcomeSplashVisible && shouldShowWelcome && (route == null || route == Routes.Home)) {
            WelcomeToOptireaderSplash(
                onFinished = {
                    welcomeSplashVisible = false
                    scope.launch {
                        preferencesRepository.setPendingFirstImportOnboarding(true)
                    }
                    mainTabIndex = MainTab.Menu
                    navigateToHomeFromTabs()
                    showGlobalSettingsDialog = true
                },
            )
        }
        firstImportWizardSources?.let { sources ->
            FirstImportWizardDialog(
                sources = sources,
                onDismiss = { firstImportWizardSources = null },
                onConfirm = { items ->
                    firstImportWizardSources = null
                    viewModel.completeFirstImportWizard(items) {
                        mainTabIndex = MainTab.Tbr
                    }
                },
            )
        }
    }
}

@Composable
private fun LibraryFilterDialog(
    initial: LibraryFilterState,
    onDismiss: () -> Unit,
    onApply: (LibraryFilterState) -> Unit,
) {
    var minReadsText by remember(initial) { mutableStateOf(initial.minReads?.toString().orEmpty()) }
    var ratingMinText by remember(initial) { mutableStateOf(initial.ratingMin?.toString().orEmpty()) }
    var ratingMaxText by remember(initial) { mutableStateOf(initial.ratingMax?.toString().orEmpty()) }
    var finishFilter by remember(initial) { mutableStateOf(initial.finishFilter) }
    var formatFilter by remember(initial) { mutableStateOf(initial.formatFilter) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Library filters") },
        text = {
            Column {
                OutlinedTextField(
                    value = minReadsText,
                    onValueChange = { minReadsText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Minimum number of reads") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ratingMinText,
                    onValueChange = { ratingMinText = it },
                    label = { Text("Rating lower bound (0-10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ratingMaxText,
                    onValueChange = { ratingMaxText = it },
                    label = { Text("Rating upper bound (0-10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(12.dp))
                Text("Finished status")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = finishFilter == FinishFilter.Any, onClick = { finishFilter = FinishFilter.Any })
                    Text("Any")
                    Spacer(Modifier.width(10.dp))
                    RadioButton(selected = finishFilter == FinishFilter.Finished, onClick = { finishFilter = FinishFilter.Finished })
                    Text("Finished")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = finishFilter == FinishFilter.Unfinished, onClick = { finishFilter = FinishFilter.Unfinished })
                    Text("Unfinished")
                }
                Spacer(Modifier.height(8.dp))
                Text("Format")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = formatFilter == FormatFilter.Any, onClick = { formatFilter = FormatFilter.Any })
                    Text("Any")
                    Spacer(Modifier.width(10.dp))
                    RadioButton(selected = formatFilter == FormatFilter.Epub, onClick = { formatFilter = FormatFilter.Epub })
                    Text("EPUB")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = formatFilter == FormatFilter.Pdf, onClick = { formatFilter = FormatFilter.Pdf })
                    Text("PDF")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        LibraryFilterState(
                            minReads = minReadsText.toIntOrNull(),
                            ratingMin = ratingMinText.toFloatOrNull()?.coerceIn(0f, 10f),
                            ratingMax = ratingMaxText.toFloatOrNull()?.coerceIn(0f, 10f),
                            finishFilter = finishFilter,
                            formatFilter = formatFilter,
                        ),
                    )
                },
            ) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onApply(LibraryFilterState())
                    },
                ) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun TbrFilterDialog(
    initial: TbrFilterState,
    onDismiss: () -> Unit,
    onApply: (TbrFilterState) -> Unit,
) {
    var sortMode by remember(initial) { mutableStateOf(initial.sortMode) }
    var startedFilter by remember(initial) { mutableStateOf(initial.startedFilter) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TBR sort and filters") },
        text = {
            Column {
                Text("Sort by")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sortMode == TbrSortMode.ImportNewest, onClick = { sortMode = TbrSortMode.ImportNewest })
                    Text("Import time (newest)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sortMode == TbrSortMode.ImportOldest, onClick = { sortMode = TbrSortMode.ImportOldest })
                    Text("Import time (oldest)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sortMode == TbrSortMode.FileNameAsc, onClick = { sortMode = TbrSortMode.FileNameAsc })
                    Text("File name (A-Z)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sortMode == TbrSortMode.FileNameDesc, onClick = { sortMode = TbrSortMode.FileNameDesc })
                    Text("File name (Z-A)")
                }
                Spacer(Modifier.height(10.dp))
                Text("Started")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = startedFilter == TbrStartedFilter.Started, onCheckedChange = {
                        startedFilter = if (it) TbrStartedFilter.Started else TbrStartedFilter.Any
                    })
                    Text("Started only")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = startedFilter == TbrStartedFilter.NotStarted, onCheckedChange = {
                        startedFilter = if (it) TbrStartedFilter.NotStarted else TbrStartedFilter.Any
                    })
                    Text("Not started only")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(TbrFilterState(sortMode = sortMode, startedFilter = startedFilter)) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply(TbrFilterState()) }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

private enum class LibraryOverlayMode {
    BookInfo,
    Stats,
}

/** True if this TBR tile has shelf/reader history worth showing the stats action for. */
private fun tbrBookEligibleForStatsIcon(context: android.content.Context, book: BookEntity): Boolean {
    if (BookStatsStore.hasPersistedStats(context, book.id)) return true
    if (book.readProgress01 > 0.001f) return true
    if (book.rating10 != null) return true
    if (ReaderReadingTime.loadTotalSeconds(context, book.id) > 0L) return true
    return false
}

@Composable
private fun ShelfLibraryOrTbrBookOverlays(
    visible: Boolean,
    libraryStatsContext: LibraryBookStatsContext?,
    libraryOverlayMode: LibraryOverlayMode?,
    onLibraryStatsContextChange: (LibraryBookStatsContext?) -> Unit,
    onLibraryOverlayModeChange: (LibraryOverlayMode?) -> Unit,
    viewModel: OptireaderViewModel,
) {
    if (!visible) return
    val ctx = libraryStatsContext ?: return
    when (libraryOverlayMode) {
        LibraryOverlayMode.Stats -> {
            LibraryBookStatsOverlay(
                book = ctx.book,
                info = ctx.info,
                totalReadingSeconds = ctx.totalReadingSeconds,
                avgWpmUser = ctx.avgWpmUser,
                avgWpmBook = ctx.avgWpmBook,
                currentProgress01 = ctx.book.readProgress01,
                onDismiss = {
                    onLibraryStatsContextChange(null)
                    onLibraryOverlayModeChange(null)
                },
                onRatingChanged = { rating ->
                    viewModel.updateBookRating(ctx.book.id, rating)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        LibraryOverlayMode.BookInfo, null -> {
            BookInfoPageOverlay(
                book = ctx.book,
                info = ctx.info,
                coverPath = ctx.book.coverPath,
                totalReadingSeconds = ctx.totalReadingSeconds,
                sessionReadingSeconds = 0L,
                validReadingSecondsForWpm = ctx.totalReadingSeconds,
                historyDays = ctx.historyDays,
                avgWpmUser = ctx.avgWpmUser,
                avgWpmBook = ctx.avgWpmBook,
                currentProgress01 = ctx.book.readProgress01,
                onOpenChapterLink = null,
                onDismiss = {
                    onLibraryStatsContextChange(null)
                    onLibraryOverlayModeChange(null)
                },
                onRatingChanged = { rating ->
                    viewModel.updateBookRating(ctx.book.id, rating)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LibraryMainTabWithOverlays(
    shelfItems: List<ShelfItem>,
    showReadingProgress: Boolean,
    isDayTheme: Boolean,
    useAmoledDark: Boolean,
    onNavigateToReader: (Long) -> Unit,
    onNavigateToFolder: (Long) -> Unit,
    viewModel: OptireaderViewModel,
    onBookLongPress: (Long) -> Unit,
) {
    LibraryScreen(
        shelfItems = shelfItems,
        showReadingProgress = showReadingProgress,
        isDayTheme = isDayTheme,
        useAmoledDark = useAmoledDark,
        onBookClick = onNavigateToReader,
        onOpenFolder = onNavigateToFolder,
        onShelfOrderCommitted = viewModel::persistShelfOrder,
        onMergeBookOntoTarget = viewModel::mergeBookOntoShelfTarget,
        dragAndMergeEnabled = true,
        onBookLongPress = onBookLongPress,
        showRatingStamp = true,
    )
}

private fun quickLibraryBookStatsContext(
    context: android.content.Context,
    book: BookEntity,
): LibraryBookStatsContext {
    val file = java.io.File(book.localPath)
    val totalUnits = when (book.format.lowercase()) {
        "pdf" -> 1
        else -> 1
    }
    return LibraryBookStatsContext(
        book = book,
        info = BookInfoUiData(
            chapters = emptyList(),
            chapterPageIndexBySpine = emptyMap(),
            title = book.title,
            author = "Unknown",
            creator = "Unknown",
            fileName = file.name,
            filePath = book.localPath,
            fileSizeBytes = file.length(),
            totalUnits = totalUnits,
            currentUnit = ((book.readProgress01.coerceIn(0f, 1f)) * totalUnits).toInt().coerceIn(1, totalUnits),
            wordsCount = 0,
            charsCount = 0,
        ),
        totalReadingSeconds = ReaderReadingTime.loadTotalSeconds(context, book.id),
        historyDays = 1,
        avgWpmUser = 0f,
        avgWpmBook = 0f,
    )
}

@Composable
private fun ReaderRoute(
    bookId: Long,
    loadBook: suspend (Long) -> BookEntity?,
    beginRereadFromComplete: suspend (Long) -> Unit,
    epubPageStyle: EpubPageStyle,
    onShelfProgressWhenBookLoaded: (Float) -> Unit,
    onReadingProgress: (Float) -> Unit,
    onRatingChanged: (Float?) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var book by remember(bookId) { mutableStateOf<BookEntity?>(null) }
    var rereadPromptHandled by rememberSaveable(bookId) { mutableStateOf(false) }
    var epubNavigatorResumeKey by rememberSaveable(bookId) { mutableStateOf(0) }
    var isBeginningReread by remember { mutableStateOf(false) }
    LaunchedEffect(bookId) {
        book = loadBook(bookId)
    }
    /** Snapshot shelf progress for TBR recommendations whenever the loaded book row updates (open or reread). */
    LaunchedEffect(bookId, book?.readProgress01) {
        val bb = book ?: return@LaunchedEffect
        onShelfProgressWhenBookLoaded(bb.readProgress01.coerceIn(0f, 1f))
    }
    val b = book
    val completeThreshold = 0.999f
    if (b == null || isBeginningReread) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else if (!rereadPromptHandled && b.readProgress01 >= completeThreshold) {
        BackHandler { rereadPromptHandled = true }
        AlertDialog(
            onDismissRequest = { rereadPromptHandled = true },
            title = { Text(stringResource(R.string.reader_reread_title)) },
            text = { Text(stringResource(R.string.reader_reread_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            isBeginningReread = true
                            try {
                                runCatching {
                                    beginRereadFromComplete(bookId)
                                    book = loadBook(bookId)
                                    epubNavigatorResumeKey = epubNavigatorResumeKey + 1
                                    rereadPromptHandled = true
                                }
                            } finally {
                                isBeginningReread = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.reader_reread_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { rereadPromptHandled = true }) {
                    Text(stringResource(R.string.reader_reread_no))
                }
            },
        )
    } else {
        var liveProgress01 by remember(bookId) { mutableStateOf<Float?>(null) }
        LaunchedEffect(bookId, b.readProgress01) {
            liveProgress01 = b.readProgress01.coerceIn(0f, 1f)
        }
        var showExitRatingDialog by rememberSaveable(bookId) { mutableStateOf(false) }
        var exitRatingText by remember(bookId) { mutableStateOf("") }

        val handleExitRequest: () -> Unit = {
            val prog = (liveProgress01 ?: b.readProgress01).coerceIn(0f, 1f)
            if (prog >= completeThreshold) {
                exitRatingText = b.rating10?.let { String.format(Locale.US, "%.1f", it) } ?: ""
                showExitRatingDialog = true
            } else {
                onBack()
            }
        }

        Box(Modifier.fillMaxSize()) {
            ReaderScreen(
                book = b,
                epubPageStyle = epubPageStyle,
                onBack = handleExitRequest,
                onReadingProgress = { fraction ->
                    val f = fraction.coerceIn(0f, 1f)
                    liveProgress01 = f
                    onReadingProgress(f)
                },
                onRatingChanged = onRatingChanged,
                epubNavigatorResumeKey = epubNavigatorResumeKey,
            )
            if (showExitRatingDialog) {
                AlertDialog(
                    onDismissRequest = { showExitRatingDialog = false },
                    title = { Text(stringResource(R.string.reader_exit_rating_title)) },
                    text = {
                        Column {
                            Text(
                                text = stringResource(R.string.reader_exit_rating_message),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = exitRatingText,
                                onValueChange = { exitRatingText = it },
                                label = { Text(stringResource(R.string.first_import_rating_hint)) },
                                placeholder = { Text(stringResource(R.string.rating_na)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val trimmed = exitRatingText.trim()
                                val isNA = trimmed.isEmpty() ||
                                    trimmed.equals("NA", ignoreCase = true) ||
                                    trimmed.equals("N/A", ignoreCase = true)
                                val rRating: Float? = if (isNA) {
                                    null
                                } else {
                                    trimmed.toFloatOrNull()?.coerceIn(0f, 10f)
                                }
                                if (!isNA && rRating == null) return@TextButton
                                onRatingChanged(rRating)
                                showExitRatingDialog = false
                                onBack()
                            },
                        ) {
                            Text(stringResource(R.string.reader_exit_rating_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitRatingDialog = false }) {
                            Text(stringResource(R.string.reader_exit_rating_cancel))
                        }
                    },
                )
            }
        }
        BackHandler {
            if (showExitRatingDialog) {
                showExitRatingDialog = false
            } else {
                handleExitRequest()
            }
        }
    }
}
