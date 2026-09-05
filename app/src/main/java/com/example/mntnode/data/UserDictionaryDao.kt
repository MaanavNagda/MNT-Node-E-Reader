package com.example.mntnode.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDictionaryDao {
    @Query("SELECT * FROM user_dictionary WHERE word = :word LIMIT 1")
    suspend fun getByWord(word: String): UserDictionaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: UserDictionaryEntity)
}
