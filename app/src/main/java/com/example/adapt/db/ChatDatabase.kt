package com.example.adapt.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlin.jvm.java

@Database(entities = [ChatModel::class], version = 1, exportSchema = false)
abstract class ChatDatabase: RoomDatabase() {


    abstract fun chatDao(): ChatDao

    companion object{
        private var INSTANCE: ChatDatabase? = null

        fun getDatabase(context: Context): ChatDatabase{
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, ChatDatabase::class.java, "GiChatDB"
                )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}