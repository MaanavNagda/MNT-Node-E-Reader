package com.example.mntnode.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shelf_folders")
data class ShelfFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shelfOrder: Int = 0,
    val name: String = "",
)
