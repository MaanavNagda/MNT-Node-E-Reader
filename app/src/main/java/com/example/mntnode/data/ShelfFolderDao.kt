package com.example.mntnode.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfFolderDao {
    @Query("SELECT * FROM shelf_folders ORDER BY shelfOrder ASC, id ASC")
    fun observeAll(): Flow<List<ShelfFolderEntity>>

    @Query("SELECT * FROM shelf_folders ORDER BY shelfOrder ASC, id ASC")
    suspend fun getAllSorted(): List<ShelfFolderEntity>

    @Query("SELECT IFNULL(MAX(shelfOrder), -1) FROM shelf_folders")
    suspend fun maxShelfOrder(): Int

    @Insert
    suspend fun insert(folder: ShelfFolderEntity): Long

    @Update
    suspend fun update(folder: ShelfFolderEntity)

    @Query("UPDATE shelf_folders SET shelfOrder = :order WHERE id = :id")
    suspend fun updateShelfOrder(id: Long, order: Int)

    @Query("UPDATE shelf_folders SET name = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("SELECT * FROM shelf_folders WHERE id = :id")
    suspend fun getById(id: Long): ShelfFolderEntity?

    @Query("DELETE FROM shelf_folders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
