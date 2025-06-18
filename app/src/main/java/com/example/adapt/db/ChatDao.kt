package com.example.adapt.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
abstract class ChatDao {

    @Insert
    abstract suspend fun create(chatModel: ChatModel)

    @Query("SELECT * FROM chats ORDER BY id")
    abstract fun readAllChats(): LiveData<List<ChatModel>>

    @Query("SELECT * FROM chats ORDER BY id")
    abstract suspend fun getAllChatsList(): List<ChatModel>
}