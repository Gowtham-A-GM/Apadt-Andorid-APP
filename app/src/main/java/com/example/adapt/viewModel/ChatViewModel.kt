package com.example.adapt.viewModel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.example.adapt.db.ChatDao
import com.example.adapt.db.ChatDatabase
import com.example.adapt.db.ChatModel
import com.example.adapt.tts.TTSManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val chatDao: ChatDao = ChatDatabase.getDatabase(application).chatDao()
    val allChats: LiveData<List<ChatModel>> = chatDao.readAllChats()
    private val customQueryDao = ChatDatabase.getDatabase(application).customQueryDao()

    suspend fun getCustomResponseIfExists(input: String): String? {
        return withContext(Dispatchers.IO) {
            customQueryDao.getResponseForKeyword(input.trim().lowercase())
        }
    }

    suspend fun getResponseForKeyword(keyword: String): String? {
        return customQueryDao.getResponseByKeyword(keyword)
    }

    companion object {
        var hasIntroInitialized = false
    }

    private var ttsManager: TTSManager? = null

    fun initTTS(context: Context, onStart: () -> Unit, onDone: () -> Unit) {
        if (ttsManager == null) {
            ttsManager = TTSManager(context, onStart, onDone)
        }
    }

    fun speakOut(text: String, lang: String) {
        ttsManager?.speak(text, lang)
    }

    fun updateLanguage(langCode: String) {
        ttsManager?.updateLanguage(langCode)
    }

    fun shutdownTTS() {
        ttsManager?.shutdown()
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager?.shutdown()
    }

    suspend fun initGeminiAI(language: String) {
        try {
            val reply = GeminiHandler.initGeminiAI(language)
            create(ChatModel(message = reply, isUser = false))
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Error in Gemini communication", e)
        }
    }

    suspend fun sendMessageToGeminiAI(prompt: String, language: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val historyModels = allChats.value ?: emptyList()
                val historyPairs = historyModels.map {
                    val role = if (it.isUser) "user" else "model"
                    role to it.message
                }
                val reply = GeminiHandler.sendMessage(prompt, language, historyPairs)
//                create(ChatModel(message = reply, isUser = false))
                reply
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in Gemini communication", e)
                if (language == "ta-IN") "மன்னிக்கவும், பதிலை உருவாக்க முடியவில்லை."
                else "Sorry, something went wrong."
            }
        }
    }

    fun create(chatModel: ChatModel) = viewModelScope.launch {
        chatDao.create(chatModel)
    }
}
