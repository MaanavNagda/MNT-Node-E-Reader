package com.example.optireader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN sourceAbsolutePath TEXT")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN readProgress01 REAL NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_dictionary (
                word TEXT NOT NULL PRIMARY KEY,
                definition TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN shelfOrder INTEGER NOT NULL DEFAULT 0")
        val c = db.query("SELECT id FROM books ORDER BY title COLLATE NOCASE ASC, id ASC")
        var i = 0
        while (c.moveToNext()) {
            val id = c.getLong(0)
            db.execSQL("UPDATE books SET shelfOrder=$i WHERE id=$id")
            i++
        }
        c.close()
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shelf_folders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                shelfOrder INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("ALTER TABLE books ADD COLUMN folderId INTEGER")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shelf_folders ADD COLUMN name TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN librarySection INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE books ADD COLUMN importedAtMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE books SET importedAtMillis = id WHERE importedAtMillis = 0")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN rating10 REAL")
    }
}

/** Removes shelf folders and the books.folderId column; flattens in-folder books onto the main shelf. */
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE books SET shelfOrder = 900000 + id WHERE folderId IS NOT NULL")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS books_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                format TEXT NOT NULL,
                localPath TEXT NOT NULL,
                coverPath TEXT,
                sourceAbsolutePath TEXT,
                readProgress01 REAL NOT NULL,
                shelfOrder INTEGER NOT NULL,
                librarySection INTEGER NOT NULL,
                importedAtMillis INTEGER NOT NULL,
                rating10 REAL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO books_new (id, title, format, localPath, coverPath, sourceAbsolutePath, readProgress01, shelfOrder, librarySection, importedAtMillis, rating10)
            SELECT id, title, format, localPath, coverPath, sourceAbsolutePath, readProgress01, shelfOrder, librarySection, importedAtMillis, rating10 FROM books
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE books")
        db.execSQL("ALTER TABLE books_new RENAME TO books")
        db.execSQL("DROP TABLE IF EXISTS shelf_folders")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS shelf_folders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                shelfOrder INTEGER NOT NULL,
                name TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL("ALTER TABLE books ADD COLUMN folderId INTEGER")
    }
}

@Database(
    entities = [BookEntity::class, ShelfFolderEntity::class, UserDictionaryEntity::class],
    version = 11,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun shelfFolderDao(): ShelfFolderDao
    abstract fun userDictionaryDao(): UserDictionaryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "optireader.db",
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                    )
                    .build().also { instance = it }
            }
        }
    }
}
