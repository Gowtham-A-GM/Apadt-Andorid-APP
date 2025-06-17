//package com.example.adapt.viewModel
//
//import android.app.Application
//import android.speech.tts.TextToSpeech
//import android.util.Log
//import android.widget.Toast
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.viewModelScope
//import com.example.adapt.db.ChatDao
//import com.example.adapt.db.ChatDatabase
//import com.example.adapt.db.ChatModel
//import com.google.ai.client.generativeai.GenerativeModel
//import com.google.ai.client.generativeai.type.content
//import kotlinx.coroutines.launch
//import java.util.Locale
//
//class ChatViewModel(application: Application) : AndroidViewModel(application) {
//
//    private val chatDao: ChatDao = ChatDatabase.getDatabase(application).chatDao()
//    val allChats: LiveData<List<ChatModel>> = chatDao.readAllChats()
//    private val app = application
//
//    // Text-to-Speech (TTS)
//    private var textToSpeech: TextToSpeech? = null
//    private var isTTSReady = false
//    private var isMuted: Boolean = false
//    private var lastReply: String? = null
//    private var wasInterrupted: Boolean = false
//
//    init {
//        textToSpeech = TextToSpeech(app) { status ->
//            if (status == TextToSpeech.SUCCESS) {
//                val result = textToSpeech?.setLanguage(Locale.US)
//                isTTSReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
//                if (!isTTSReady) {
//                    Toast.makeText(app, "TTS language not supported", Toast.LENGTH_SHORT).show()
//                }
//            } else {
//                Toast.makeText(app, "TTS initialization failed", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    fun create(chatModel: ChatModel) = viewModelScope.launch {
//        chatDao.create(chatModel)
//    }
//
//    suspend fun createAndReturnId(chatModel: ChatModel): Long {
//        return chatDao.createAndReturnId(chatModel)
//    }
//
//    fun updateCurrentChat(id: Long, newMessage: String) = viewModelScope.launch {
//        chatDao.updateCurrentChat(id, newMessage)
//    }
//
//    fun deleteChatById(id: Long) = viewModelScope.launch {
//        chatDao.deleteChatById(id)
//    }
//
//    private val generativeModel: GenerativeModel = GenerativeModel(
//        modelName = "gemini-1.5-flash",
//        apiKey = ""
//    )
//
//    fun setMuted(muted: Boolean) {
//        isMuted = muted
//        if (muted && textToSpeech?.isSpeaking == true) {
//            textToSpeech?.stop()
//            wasInterrupted = true
//        } else if (!muted && wasInterrupted) {
//            speakLastReplyAgain()
//            wasInterrupted = false
//        }
//    }
//
//    private fun speakLastReplyAgain() {
//        if (isTTSReady && !isMuted && lastReply != null) {
//            Log.d("TTS", "Resuming last reply: $lastReply")
//            textToSpeech?.speak(lastReply, TextToSpeech.QUEUE_FLUSH, null, null)
//        }
//    }
//
//    fun sendMessageToGeminiAI(prompt: String, chatNo: Long) {
//        viewModelScope.launch {
//            var insertedId: Long? = null
//            try {
//                val historyList = chatDao.getChatsByChatNo(chatNo)
//                val historyContent = historyList.map {
//                    val role = if (it.isUser) "user" else "model"
//                    content(role) { text(it.message) }
//                }
//                val geminiChat = generativeModel.startChat(history = historyContent)
//
//                val placeholder = ChatModel(message = "Typing...", isUser = false, chatNo = chatNo)
//                insertedId = createAndReturnId(placeholder)
//
//                val response = geminiChat.sendMessage(prompt)
//                val reply = response.text ?: "No response"
//                updateCurrentChat(insertedId, reply)
//
//                lastReply = reply
//                wasInterrupted = false
//                if (isTTSReady && !isMuted) {
//                    Log.d("TTS", "Speaking: $reply")
//                    textToSpeech?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null)
//                } else {
//                    Log.d("TTS", "TTS not triggered. isTTSReady=$isTTSReady, isMuted=$isMuted")
//                }
//
//            } catch (e: Exception) {
//                insertedId?.let { deleteChatById(it) }
//
//                val message = if (e.message?.contains("Unable to resolve host", true) == true ||
//                    e.message?.contains("Failed to connect", true) == true ||
//                    e.message?.contains("timeout", true) == true
//                ) {
//                    "No internet connection. Please check your network."
//                } else {
//                    "Unknown error occurred. Please try again."
//                }
//
//                Toast.makeText(app, message, Toast.LENGTH_LONG).show()
//                Log.e("Gemini Error", "Exception occurred: ${e.message}", e)
//            }
//        }
//    }
//
//    override fun onCleared() {
//        textToSpeech?.stop()
//        textToSpeech?.shutdown()
//        super.onCleared()
//    }
//}
