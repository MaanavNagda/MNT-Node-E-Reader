package com.example.optireader.data

import android.content.Context
import androidx.room.withTransaction
import com.example.optireader.covers.CoverExtractor
import com.example.optireader.epub.EpubOpfParser
import com.example.optireader.scan.DiscoveredSource
import com.example.optireader.ui.BookStatsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class LibraryRepository(
    private val context: Context,
    private val db: AppDatabase,
) {
    private val bookDao = db.bookDao()
    private val folderDao = db.shelfFolderDao()

    fun observeShelf(): Flow<List<ShelfItem>> = combine(
        folderDao.observeAll(),
        bookDao.observeAllRows(),
    ) { folders, books -> ShelfItem.buildList(folders, books) }

    fun observeTbrShelf(): Flow<List<ShelfItem>> =
        bookDao.observeTbrStandaloneBooks().map { books -> books.map { ShelfItem.BookTile(it) } }

    fun observeBooksInFolder(folderId: Long): Flow<List<BookEntity>> =
        bookDao.observeBooksInFolder(folderId)

    fun observeFolder(folderId: Long): Flow<ShelfFolderEntity?> =
        folderDao.observeAll().map { list -> list.firstOrNull { it.id == folderId } }

    suspend fun getShelfFolder(id: Long): ShelfFolderEntity? = folderDao.getById(id)

    suspend fun renameFolder(folderId: Long, name: String) = withContext(Dispatchers.IO) {
        folderDao.updateName(folderId, name.trim())
    }

    suspend fun applyFolderBookOrder(folderId: Long, orderedBookIds: List<Long>) = withContext(Dispatchers.IO) {
        if (orderedBookIds.isEmpty()) return@withContext
        db.withTransaction {
            orderedBookIds.forEachIndexed { index, id ->
                val b = bookDao.getById(id) ?: return@forEachIndexed
                if (b.folderId == folderId) {
                    bookDao.updateShelfOrder(id, index)
                }
            }
        }
    }

    suspend fun getBook(id: Long) = bookDao.getById(id)

    /**
     * Removes the book from the database and deletes its book file and cover from app storage.
     * SharedPreferences reading stats ([com.example.optireader.ui.BookStatsStore], reader time, etc.)
     * are intentionally left intact so exports / history keyed by book id are preserved.
     */
    suspend fun deleteBookPermanentlyKeepingStats(bookId: Long) = withContext(Dispatchers.IO) {
        val book = bookDao.getById(bookId) ?: return@withContext
        val localPath = book.localPath
        val coverPath = book.coverPath
        val folderId = book.folderId

        if (folderId != null) {
            db.withTransaction {
                bookDao.deleteById(bookId)
                val folder = folderDao.getById(folderId) ?: return@withTransaction
                val remaining = bookDao.getBooksInFolderSorted(folderId)
                when {
                    remaining.isEmpty() -> folderDao.deleteById(folderId)
                    remaining.size == 1 -> {
                        val lone = remaining.first()
                        bookDao.update(lone.copy(folderId = null, shelfOrder = folder.shelfOrder))
                        folderDao.deleteById(folderId)
                    }
                    else -> {
                        remaining.sortedBy { it.shelfOrder }.forEachIndexed { idx, b ->
                            bookDao.updateShelfOrder(b.id, idx)
                        }
                    }
                }
            }
            compactTopLevelShelfOrders()
        } else {
            bookDao.deleteById(bookId)
            compactTopLevelShelfOrders()
        }

        runCatching { File(localPath).delete() }
        coverPath?.let { runCatching { File(it).delete() } }
    }

    suspend fun getAllMainSectionBooks(): List<BookEntity> = withContext(Dispatchers.IO) {
        bookDao.getAllMainSectionBooks()
    }

    suspend fun getAllTbrBooks(): List<BookEntity> = withContext(Dispatchers.IO) {
        bookDao.getAllTbrBooks()
    }

    suspend fun getAllFoldersSorted(): List<ShelfFolderEntity> = withContext(Dispatchers.IO) {
        folderDao.getAllSorted()
    }

    suspend fun updateReadProgress(bookId: Long, fraction: Float) = withContext(Dispatchers.IO) {
        val book = bookDao.getById(bookId) ?: return@withContext
        val p = fraction.coerceIn(0f, 1f)
        if (book.librarySection == BookEntity.SECTION_TBR && p > 0f) {
            val nextOrder = maxOf(folderDao.maxShelfOrder(), bookDao.maxStandaloneShelfOrder()) + 1
            bookDao.update(
                book.copy(
                    readProgress01 = p,
                    librarySection = BookEntity.SECTION_MAIN,
                    shelfOrder = nextOrder,
                ),
            )
            compactTopLevelShelfOrders()
        } else {
            bookDao.updateReadProgress(bookId, p)
        }
    }

    suspend fun updateBookRating(bookId: Long, rating10: Float?) = withContext(Dispatchers.IO) {
        bookDao.updateRating10(bookId, rating10)
    }

    /**
     * After [import], book is on TBR. Promotes to main library at 100% with rating and seeded read sessions.
     */
    suspend fun promoteImportedTbrBookToLibraryFinished(
        bookId: Long,
        rating10: Float?,
        completedReadCount: Int,
    ) = withContext(Dispatchers.IO) {
        val book = bookDao.getById(bookId) ?: return@withContext
        val nextOrder = maxOf(folderDao.maxShelfOrder(), bookDao.maxStandaloneShelfOrder()) + 1
        bookDao.update(
            book.copy(
                readProgress01 = 1f,
                librarySection = BookEntity.SECTION_MAIN,
                shelfOrder = nextOrder,
                rating10 = rating10?.coerceIn(0f, 10f),
            ),
        )
        compactTopLevelShelfOrders()
        BookStatsStore.seedCompletedReadSessions(context, bookId, completedReadCount.coerceAtLeast(1))
    }

    /**
     * Moves a standalone book (or removes it from a folder then moves) to the to-read list.
     * [BookEntity.readProgress01], [BookEntity.rating10], and SharedPreferences stats keyed by
     * book id are left unchanged so progress and history are preserved on TBR.
     */
    suspend fun moveStandaloneBookToTbr(bookId: Long) = withContext(Dispatchers.IO) {
        val book = bookDao.getById(bookId) ?: return@withContext
        if (book.librarySection == BookEntity.SECTION_TBR) return@withContext
        val now = System.currentTimeMillis()
        if (book.folderId != null) {
            db.withTransaction {
                val fid = book.folderId!!
                bookDao.update(
                    book.copy(
                        folderId = null,
                        librarySection = BookEntity.SECTION_TBR,
                        importedAtMillis = now,
                        shelfOrder = 0,
                        readProgress01 = book.readProgress01,
                        rating10 = book.rating10,
                    ),
                )
                val folder = folderDao.getById(fid) ?: return@withTransaction
                val remaining = bookDao.getBooksInFolderSorted(fid)
                when {
                    remaining.isEmpty() -> folderDao.deleteById(fid)
                    remaining.size == 1 -> {
                        val lone = remaining.first()
                        bookDao.update(lone.copy(folderId = null, shelfOrder = folder.shelfOrder))
                        folderDao.deleteById(fid)
                    }
                    else -> {
                        remaining.sortedBy { it.shelfOrder }.forEachIndexed { idx, b ->
                            bookDao.updateShelfOrder(b.id, idx)
                        }
                    }
                }
            }
            compactTopLevelShelfOrders()
        } else {
            bookDao.update(
                book.copy(
                    librarySection = BookEntity.SECTION_TBR,
                    importedAtMillis = now,
                    shelfOrder = 0,
                    readProgress01 = book.readProgress01,
                    rating10 = book.rating10,
                ),
            )
            compactTopLevelShelfOrders()
        }
    }

    suspend fun getImportedSourcePaths(): Set<String> = withContext(Dispatchers.IO) {
        bookDao.getAllSourcePaths().toSet()
    }

    suspend fun applyTopLevelShelfOrder(slots: List<TopLevelSlot>) = withContext(Dispatchers.IO) {
        if (slots.isEmpty()) return@withContext
        slots.forEachIndexed { index, slot ->
            if (slot.isFolder) {
                folderDao.updateShelfOrder(slot.id, index)
            } else {
                bookDao.updateShelfOrder(slot.id, index)
            }
        }
    }

    suspend fun mergeStandaloneBooksIntoFolder(draggedBookId: Long, targetBookId: Long) =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val dragged = bookDao.getById(draggedBookId) ?: return@withTransaction
                val target = bookDao.getById(targetBookId) ?: return@withTransaction
                if (dragged.folderId != null || target.folderId != null) return@withTransaction
                if (dragged.librarySection != BookEntity.SECTION_MAIN ||
                    target.librarySection != BookEntity.SECTION_MAIN
                ) {
                    return@withTransaction
                }
                if (draggedBookId == targetBookId) return@withTransaction

                val folderId = folderDao.insert(
                    ShelfFolderEntity(shelfOrder = target.shelfOrder, name = ""),
                )
                bookDao.update(target.copy(folderId = folderId, shelfOrder = 0))
                bookDao.update(dragged.copy(folderId = folderId, shelfOrder = 1))
                compactTopLevelShelfOrders()
            }
        }

    suspend fun addStandaloneBookToFolder(bookId: Long, folderId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val book = bookDao.getById(bookId) ?: return@withTransaction
            if (book.folderId != null) return@withTransaction
            if (book.librarySection != BookEntity.SECTION_MAIN) return@withTransaction
            val maxIn = bookDao.maxShelfOrderInFolder(folderId)
            bookDao.update(book.copy(folderId = folderId, shelfOrder = maxIn + 1))
            compactTopLevelShelfOrders()
        }
    }

    /**
     * Removes a book from its folder onto the main library shelf as a standalone tile.
     * If the folder becomes empty it is deleted; if one book remains it is promoted to standalone.
     */
    suspend fun removeBookFromFolderToMainShelf(bookId: Long) = withContext(Dispatchers.IO) {
        val book = bookDao.getById(bookId) ?: return@withContext
        val fid = book.folderId ?: return@withContext
        if (book.librarySection != BookEntity.SECTION_MAIN) return@withContext
        db.withTransaction {
            val nextTopOrder =
                maxOf(folderDao.maxShelfOrder(), bookDao.maxStandaloneShelfOrder()) + 1
            bookDao.update(
                book.copy(
                    folderId = null,
                    shelfOrder = nextTopOrder,
                ),
            )
            val folder = folderDao.getById(fid) ?: return@withTransaction
            val remaining = bookDao.getBooksInFolderSorted(fid)
            when {
                remaining.isEmpty() -> folderDao.deleteById(fid)
                remaining.size == 1 -> {
                    val lone = remaining.first()
                    bookDao.update(lone.copy(folderId = null, shelfOrder = folder.shelfOrder))
                    folderDao.deleteById(fid)
                }
                else -> {
                    remaining.sortedBy { it.shelfOrder }.forEachIndexed { idx, b ->
                        bookDao.updateShelfOrder(b.id, idx)
                    }
                }
            }
        }
        compactTopLevelShelfOrders()
    }

    private suspend fun compactTopLevelShelfOrders() {
        val folders = folderDao.getAllSorted()
        val standalone = bookDao.getStandaloneSorted()
        val merged = (
            folders.map { Triple(true, it.id, it.shelfOrder) } +
                standalone.map { Triple(false, it.id, it.shelfOrder) }
            ).sortedBy { it.third }
        merged.forEachIndexed { i, (isFolder, id, _) ->
            if (isFolder) {
                folderDao.updateShelfOrder(id, i)
            } else {
                bookDao.updateShelfOrder(id, i)
            }
        }
    }

    suspend fun import(source: DiscoveredSource): Result<BookEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val booksDir = File(context.filesDir, "books").apply { mkdirs() }
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val ext = source.extensionLower
            val unique = UUID.randomUUID().toString()
            val dest = File(booksDir, "$unique.$ext")

            when (source) {
                is DiscoveredSource.LocalFile -> {
                    source.file.inputStream().use { input ->
                        dest.outputStream().use { input.copyTo(it) }
                    }
                }
            }

            val titleFromMeta = when (ext) {
                "epub" -> readEpubTitle(dest) ?: source.displayName.substringBeforeLast('.')
                else -> source.displayName.substringBeforeLast('.')
            }

            val now = System.currentTimeMillis()
            val entity = BookEntity(
                title = titleFromMeta,
                format = ext,
                localPath = dest.absolutePath,
                coverPath = null,
                sourceAbsolutePath = source.key,
                shelfOrder = 0,
                librarySection = BookEntity.SECTION_TBR,
                importedAtMillis = now,
            )
            val id = bookDao.insert(entity)
            val coverFile = File(coversDir, "$id.jpg")
            val coverOk = when (ext) {
                "pdf" -> CoverExtractor.writePdfCover(dest, coverFile)
                "epub" -> {
                    val tmp = File(coversDir, "$id-cover.tmp")
                    val ok = CoverExtractor.writeEpubCover(dest, tmp)
                    if (ok) {
                        if (coverFile.exists()) coverFile.delete()
                        tmp.renameTo(coverFile)
                    } else {
                        if (tmp.exists()) tmp.delete()
                    }
                    ok && coverFile.exists()
                }
                else -> false
            }
            val updated = entity.copy(id = id, coverPath = if (coverOk) coverFile.absolutePath else null)
            bookDao.update(updated)
            updated
        }
    }

    private fun readEpubTitle(file: File): String? = runCatching {
        java.util.zip.ZipFile(file).use { zip ->
            EpubOpfParser.readPackage(zip)?.title?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()
}
