package com.example.optireader.data

/**
 * One cell on the main library shelf: a book or a folder (shows up to four member covers).
 */
sealed class ShelfItem {
    abstract val gridKey: String

    data class BookTile(val book: BookEntity) : ShelfItem() {
        override val gridKey: String get() = "b${book.id}"
    }

    data class FolderTile(
        val folderId: Long,
        val books: List<BookEntity>,
        val folderShelfOrder: Int,
        val folderName: String,
    ) : ShelfItem() {
        override val gridKey: String get() = "f$folderId"
    }

    companion object {
        fun buildList(folders: List<ShelfFolderEntity>, allBooks: List<BookEntity>): List<ShelfItem> {
            val mainBooks = allBooks.filter { it.librarySection == BookEntity.SECTION_MAIN }
            val byFolder = mainBooks.filter { it.folderId != null }.groupBy { it.folderId!! }
            val standalone = mainBooks.filter { it.folderId == null }
            val folderTiles = folders.map { f ->
                FolderTile(
                    folderId = f.id,
                    books = byFolder[f.id].orEmpty().sortedBy { it.shelfOrder },
                    folderShelfOrder = f.shelfOrder,
                    folderName = f.name,
                )
            }
            val bookTiles = standalone.map { BookTile(it) }
            return (folderTiles + bookTiles).sortedBy {
                when (it) {
                    is BookTile -> it.book.shelfOrder
                    is FolderTile -> it.folderShelfOrder
                }
            }
        }
    }
}

data class TopLevelSlot(val isFolder: Boolean, val id: Long)
