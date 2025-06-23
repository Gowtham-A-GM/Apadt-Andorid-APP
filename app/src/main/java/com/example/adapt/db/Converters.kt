package com.example.adapt.db

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromFloatArray(value: FloatArray): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        return value.split(",").map { it.toFloat() }.toFloatArray()
    }
}
