package com.example.optireader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shelf_folders")
data class ShelfFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shelfOrder: Int = 0,
    val name: String = "",
)
