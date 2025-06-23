package com.example.adapt.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RegisteredFaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(face: RegisteredFaceModel)

    @Query("SELECT * FROM registered_faces")
    suspend fun getAllFaces(): List<RegisteredFaceModel>

    @Delete
    fun deleteFace(face: RegisteredFaceModel)
}
