package com.example.adapt.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import kotlin.jvm.java

@Database(
    entities = [ChatModel::class, CustomQueryModel::class, RegisteredFaceModel::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun customQueryDao(): CustomQueryDao
    abstract fun registeredFaceDao(): RegisteredFaceDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "AdaptDB"
                )
                    .fallbackToDestructiveMigration() // Needed when increasing DB version
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
