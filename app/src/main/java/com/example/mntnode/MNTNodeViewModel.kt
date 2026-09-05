package com.example.mntnode

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.os.Environment
import com.example.mntnode.data.BookEntity
import com.example.mntnode.data.LibraryRepository
import com.example.mntnode.data.ShelfFolderEntity
import com.example.mntnode.data.ShelfItem
import com.example.mntnode.data.TopLevelSlot
import kotlinx.coroutines.flow.Flow
import com.example.mntnode.data.preferences.PreferencesRepository
import com.example.mntnode.scan.DiscoveredSource
import com.example.mntnode.scan.FileSystemScanner
import com.example.mntnode.scan.ImportFuzzySearch
import com.example.mntnode.ui.BookStatsStore
import com.example.mntnode.recommendation.BookMetadataResolver
import com.example.mntnode.recommendation.TbrRecommendationEngine
import com.example.mntnode.ui.StatisticsExportPayload
import com.example.mntnode.ui.StatisticsExportTextBuilder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class ImportUiState(
    val items: List<DiscoveredSource> = emptyList(),
    val query: String = "",
    val selectedKeys: Set<String> = emptySet(),
    val scanSourceDescription: String? = null,
    val isScanning: Boolean = false,
    val isImporting: Boolean = false,
    val toast: String? = null,
)

data class FirstImportWizardReadBefore(
    /** Null = user left rating as N/A. */
    val rating10: Float?,
    val timesRead: Int,
)

data class FirstImportWizardItem(
    val source: DiscoveredSource,
    val readBefore: FirstImportWizardReadBefore?,
)

class MNTNodeViewModel(
    application: Application,
    private val repository: LibraryRepository,
    private val preferencesRepository: PreferencesRepository,
) : AndroidViewModel(application) {

    val shelfItems: StateFlow<List<ShelfItem>> = repository.observeShelf()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tbrItems: StateFlow<List<ShelfItem>> = repository.observeTbrShelf()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun folderBooks(folderId: Long): Flow<List<BookEntity>> = repository.observeBooksInFolder(folderId)

    fun observeFolder(folderId: Long): Flow<ShelfFolderEntity?> = repository.observeFolder(folderId)

    fun renameFolder(folderId: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameFolder(folderId, name)
        }
    }

    fun persistFolderBookOrder(folderId: Long, orderedKeys: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = orderedKeys.mapNotNull { key ->
                if (key.startsWith("b")) key.removePrefix("b").toLong() else null
            }
            if (ids.size == orderedKeys.size) {
                repository.applyFolderBookOrder(folderId, ids)
            }
        }
    }

    private val _import = MutableStateFlow(ImportUiState())
    val import: StateFlow<ImportUiState> = _import.asStateFlow()

    private val _statisticsExportMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val statisticsExportMessages: SharedFlow<String> = _statisticsExportMessages.asSharedFlow()

    /** Snapshot of [BookEntity.readProgress01] when the reader UI first loads a book (for TBR suggestions). */
    private val readerEntryProgress01 = ConcurrentHashMap<Long, Float>()

    fun setReaderEntryProgressSnapshot(bookId: Long, progress01: Float) {
        readerEntryProgress01[bookId] = progress01.coerceIn(0f, 1f)
    }

    fun takeReaderEntryProgressSnapshot(bookId: Long): Float? = readerEntryProgress01.remove(bookId)

    /** Export payload waiting for folder picker (survives activity recreation). */
    private var pendingStatisticsFolderExport: StatisticsExportPayload? = null

    fun setPendingStatisticsFolderExport(payload: StatisticsExportPayload) {
        pendingStatisticsFolderExport = payload
    }

    fun consumePendingStatisticsFolderExport(): StatisticsExportPayload? {
        val p = pendingStatisticsFolderExport
        pendingStatisticsFolderExport = null
        return p
    }

    fun consumeToast() {
        _import.update { it.copy(toast = null) }
    }

    fun scanStorageRoot() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            _import.update {
                it.copy(
                    isScanning = true,
                    toast = null,
                    items = emptyList(),
                    selectedKeys = emptySet(),
                    query = "",
                )
            }
            val prefs = preferencesRepository.preferencesFlow.first()
            val root = Environment.getExternalStorageDirectory()
            val (result, defaultSelected) = withContext(Dispatchers.IO) {
                val scan = FileSystemScanner.scanDirectory(
                    root,
                    includeHidden = prefs.listHiddenFiles,
                )
                val imported = repository.getImportedSourcePaths()
                val defaults = scan.items.map { it.key }.filter { it !in imported }.toSet()
                scan to defaults
            }
            val toast = when {
                result.items.isEmpty() -> app.getString(R.string.import_empty_scan)
                result.truncated -> app.getString(
                    R.string.import_scan_capped,
                    FileSystemScanner.MAX_SCAN_RESULTS,
                )
                else -> null
            }
            _import.value = ImportUiState(
                items = result.items,
                query = "",
                selectedKeys = defaultSelected,
                scanSourceDescription = app.getString(
                    R.string.import_scan_source_storage,
                    root.absolutePath,
                ),
                isScanning = false,
                toast = toast,
            )
        }
    }

    fun setQuery(q: String) {
        _import.update { it.copy(query = q) }
    }

    fun toggle(key: String) {
        _import.update { s ->
            val next = s.selectedKeys.toMutableSet()
            if (!next.add(key)) next.remove(key)
            s.copy(selectedKeys = next)
        }
    }

    fun selectAllVisible() {
        _import.update { s ->
            val visible = filterItems(s.items, s.query).map { it.key }.toSet()
            s.copy(selectedKeys = visible)
        }
    }

    fun selectNone() {
        _import.update { it.copy(selectedKeys = emptySet()) }
    }

    suspend fun loadBook(id: Long): BookEntity? = withContext(Dispatchers.IO) {
        repository.getBook(id)
    }

    suspend fun getShelfFolder(id: Long): ShelfFolderEntity? = withContext(Dispatchers.IO) {
        repository.getShelfFolder(id)
    }

    fun updateReadingProgress(bookId: Long, fraction: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateReadProgress(bookId, fraction)
            BookStatsStore.recordProgress(getApplication(), bookId, fraction)
        }
    }

    suspend fun beginRereadFromComplete(bookId: Long) = withContext(Dispatchers.IO) {
        repository.updateReadProgress(bookId, 0f)
        BookStatsStore.beginRereadSession(getApplication(), bookId)
    }

    fun updateBookRating(bookId: Long, rating10: Float?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBookRating(bookId, rating10)
        }
    }

    fun persistShelfOrder(orderedKeys: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val slots = orderedKeys.mapNotNull { key ->
                when {
                    key.startsWith("b") -> TopLevelSlot(false, key.removePrefix("b").toLong())
                    key.startsWith("f") -> TopLevelSlot(true, key.removePrefix("f").toLong())
                    else -> null
                }
            }
            if (slots.size == orderedKeys.size) {
                repository.applyTopLevelShelfOrder(slots)
            }
        }
    }

    fun mergeBookOntoShelfTarget(draggedBookId: Long, targetGridKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            when {
                targetGridKey.startsWith("b") -> {
                    val tid = targetGridKey.removePrefix("b").toLong()
                    repository.mergeStandaloneBooksIntoFolder(draggedBookId, tid)
                }
                targetGridKey.startsWith("f") -> {
                    val fid = targetGridKey.removePrefix("f").toLong()
                    repository.addStandaloneBookToFolder(draggedBookId, fid)
                }
            }
        }
    }

    fun moveBookToTbr(bookId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.moveStandaloneBookToTbr(bookId)
        }
    }

    fun removeBookFromFolderToMainShelf(bookId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeBookFromFolderToMainShelf(bookId)
        }
    }

    fun deleteBookPermanentlyKeepingStats(bookId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBookPermanentlyKeepingStats(bookId)
        }
    }

    /**
     * Returns up to three TBR books to suggest after [completedBookId] was finished in this session.
     */
    suspend fun loadTbrRecommendationsAfterComplete(completedBookId: Long): List<BookEntity> =
        withContext(Dispatchers.IO) {
            val prefs = preferencesRepository.preferencesFlow.first()
            if (!prefs.recommendationsEnabled) return@withContext emptyList()
            val completed = repository.getBook(completedBookId) ?: return@withContext emptyList()
            val tbr = repository.getAllTbrBooks()
            if (tbr.isEmpty()) return@withContext emptyList()
            val readMain = repository.getAllMainSectionBooks()
            val completedSignals = BookMetadataResolver.resolve(completed)
            val readSignals = readMain.associate { it.id to BookMetadataResolver.resolve(it) }
            val tbrSignals = tbr.associate { it.id to BookMetadataResolver.resolve(it) }
            TbrRecommendationEngine.pickTop3(
                completed = completed,
                completedSignals = completedSignals,
                readBooks = readMain,
                readSignals = readSignals,
                tbrBooks = tbr,
                tbrSignals = tbrSignals,
                anchor = prefs.recommendationAnchor,
                nowMillis = System.currentTimeMillis(),
            )
        }

    /** Snackbar / user feedback for export save (success or I/O errors). */
    fun notifyStatisticsExportMessage(message: String) {
        _statisticsExportMessages.tryEmit(message)
    }

    /** Builds reading statistics (.txt body + PDF document) and invokes [onResult] on the main thread. */
    fun requestStatisticsExport(onResult: (StatisticsExportPayload) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val payload = runCatching {
                val main = repository.getAllMainSectionBooks()
                val tbr = repository.getAllTbrBooks()
                val folders = repository.getAllFoldersSorted().associate { it.id to it.name }
                StatisticsExportTextBuilder.buildExport(app, main, tbr, folders)
            }.getOrElse { e ->
                _statisticsExportMessages.tryEmit(
                    app.getString(R.string.statistics_export_failed, e.message ?: e.javaClass.simpleName),
                )
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                onResult(payload)
            }
        }
    }

    /**
     * Starts import in the background. Returns true if import was started (non-empty selection).
     * On completion, import UI state is reset so the user must scan again before another import.
     */
    fun importSelected(): Boolean {
        val state = _import.value
        val selected = state.items.filter { it.key in state.selectedKeys }
        if (selected.isEmpty()) {
            _import.update { it.copy(toast = getApplication<Application>().getString(R.string.import_nothing_selected)) }
            return false
        }
        viewModelScope.launch {
            _import.update { it.copy(isImporting = true) }
            var ok = 0
            var failed = 0
            for (src in selected) {
                repository.import(src).onSuccess { ok++ }.onFailure { failed++ }
            }
            _import.value = ImportUiState(
                toast = getApplication<Application>().getString(
                    R.string.import_finished,
                    ok,
                    failed,
                ),
            )
        }
        return true
    }

    fun getSelectedSourcesForImport(): List<DiscoveredSource> {
        val state = _import.value
        return state.items.filter { it.key in state.selectedKeys }
    }

    /**
     * First-opening batch: import each file, promote “read before” books to the main shelf at 100% with stats.
     */
    fun completeFirstImportWizard(
        items: List<FirstImportWizardItem>,
        onFinished: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _import.update { it.copy(isImporting = true) }
            val (ok, failed) = withContext(Dispatchers.IO) {
                var o = 0
                var f = 0
                for (item in items) {
                    repository.import(item.source).onSuccess { book ->
                        o++
                        item.readBefore?.let { rb ->
                            repository.promoteImportedTbrBookToLibraryFinished(
                                book.id,
                                rb.rating10,
                                rb.timesRead,
                            )
                        }
                    }.onFailure { f++ }
                }
                preferencesRepository.setFirstImportWizardCompleted(true)
                preferencesRepository.setPendingFirstImportOnboarding(false)
                o to f
            }
            _import.value = ImportUiState(
                toast = getApplication<Application>().getString(
                    R.string.import_finished,
                    ok,
                    failed,
                ),
            )
            onFinished()
        }
    }

    companion object {
        private val PDF_OR_EPUB = setOf("pdf", "epub")

        fun filterItems(items: List<DiscoveredSource>, query: String): List<DiscoveredSource> {
            val allowedOnly = items.filter { it.extensionLower in PDF_OR_EPUB }
            val q = query.trim()
            if (q.isEmpty()) return allowedOnly
            return allowedOnly.filter { ImportFuzzySearch.matches(it.displayName, q) }
        }
    }
}

class MNTNodeViewModelFactory(
    private val application: Application,
    private val repository: LibraryRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MNTNodeViewModel::class.java)) {
            return MNTNodeViewModel(application, repository, preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
