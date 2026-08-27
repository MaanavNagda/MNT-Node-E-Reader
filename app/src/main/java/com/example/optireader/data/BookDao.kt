package com.example.optireader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books")
    fun observeAllRows(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookEntity?

    @Query(
        "SELECT sourceAbsolutePath FROM books WHERE sourceAbsolutePath IS NOT NULL AND TRIM(sourceAbsolutePath) != ''",
    )
    suspend fun getAllSourcePaths(): List<String>

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET readProgress01 = :fraction WHERE id = :id")
    suspend fun updateReadProgress(id: Long, fraction: Float)

    @Query("UPDATE books SET rating10 = :rating10 WHERE id = :id")
    suspend fun updateRating10(id: Long, rating10: Float?)

    @Query("SELECT IFNULL(MAX(shelfOrder), -1) FROM books WHERE folderId IS NULL AND librarySection = 1")
    suspend fun maxStandaloneShelfOrder(): Int

    @Query(
        "SELECT * FROM books WHERE folderId IS NULL AND librarySection = 0 ORDER BY importedAtMillis ASC, id ASC",
    )
    fun observeTbrStandaloneBooks(): Flow<List<BookEntity>>

    @Query("SELECT IFNULL(MAX(shelfOrder), -1) FROM books WHERE folderId = :folderId")
    suspend fun maxShelfOrderInFolder(folderId: Long): Int

    @Query("UPDATE books SET shelfOrder = :order WHERE id = :id")
    suspend fun updateShelfOrder(id: Long, order: Int)

    @Query("SELECT * FROM books WHERE folderId = :folderId ORDER BY shelfOrder ASC, id ASC")
    fun observeBooksInFolder(folderId: Long): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE folderId = :folderId ORDER BY shelfOrder ASC, id ASC")
    suspend fun getBooksInFolderSorted(folderId: Long): List<BookEntity>

    @Query("SELECT * FROM books WHERE folderId IS NULL AND librarySection = 1 ORDER BY shelfOrder ASC, id ASC")
    suspend fun getStandaloneSorted(): List<BookEntity>

    /** All books on the main library shelf (standalone + inside folders). */
    @Query("SELECT * FROM books WHERE librarySection = 1 ORDER BY id ASC")
    suspend fun getAllMainSectionBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE librarySection = 0 ORDER BY importedAtMillis ASC, id ASC")
    suspend fun getAllTbrBooks(): List<BookEntity>

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)
}
