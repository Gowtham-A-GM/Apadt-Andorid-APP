package com.example.adapt.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CustomQueryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuery(query: CustomQueryModel)

    @Query("SELECT * FROM custom_queries")
    suspend fun getAllQueries(): List<CustomQueryModel>

    @Query("SELECT response FROM custom_queries WHERE keyword = :input LIMIT 1")
    suspend fun getResponseForKeyword(input: String): String?

    @Query("SELECT response FROM custom_queries WHERE keyword = :keyword LIMIT 1")
    suspend fun getResponseByKeyword(keyword: String): String?

    @Delete
    suspend fun deleteQuery(query: CustomQueryModel)

    @Query("SELECT COUNT(*) FROM custom_queries WHERE LOWER(keyword) = LOWER(:keyword)")
    suspend fun isKeywordExists(keyword: String): Int


}

