package com.example.optireader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val format: String,
    val localPath: String,
    val coverPath: String?,
    /** Original path on device when imported via scan; used to skip already-imported files. */
    val sourceAbsolutePath: String? = null,
    /** 0..1 how far through the book (by page / spine item). */
    val readProgress01: Float = 0f,
    /** Lower values appear first on the main shelf (standalone) or inside a folder. */
    val shelfOrder: Int = 0,
    /** Non-null when this book is inside a shelf folder. */
    val folderId: Long? = null,
    /**
     * [SECTION_TBR] = to-read list (import lands here); [SECTION_MAIN] = main shelf (reading or finished).
     */
    val librarySection: Int = SECTION_MAIN,
    /** Wall-clock order for TBR (oldest import first). Ignored on main shelf. */
    val importedAtMillis: Long = 0L,
    /** User rating 0..10, or null if unset. */
    val rating10: Float? = null,
) {
    companion object {
        const val SECTION_TBR = 0
        const val SECTION_MAIN = 1
    }
}
