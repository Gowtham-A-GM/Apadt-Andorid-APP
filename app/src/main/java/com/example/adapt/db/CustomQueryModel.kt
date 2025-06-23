package com.example.adapt.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_queries")
data class CustomQueryModel(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val keyword: String,
    val response: String
)

