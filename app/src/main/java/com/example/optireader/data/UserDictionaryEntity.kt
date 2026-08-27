package com.example.optireader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_dictionary")
data class UserDictionaryEntity(
    /** Normalized lowercase lemma (matches WordNet lookup key). */
    @PrimaryKey val word: String,
    val definition: String,
)
