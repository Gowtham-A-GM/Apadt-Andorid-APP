package com.example.adapt.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlin.jvm.java

@Database(entities = [ChatModel::class, CustomQueryModel::class], version = 2, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatDao(): ChatDao
    abstract fun customQueryDao(): CustomQueryDao

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
