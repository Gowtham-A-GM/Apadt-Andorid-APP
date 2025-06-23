package com.example.adapt.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "registered_faces")
data class RegisteredFaceModel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val response: String,
    val embedding: String,
    val image: ByteArray?
)
