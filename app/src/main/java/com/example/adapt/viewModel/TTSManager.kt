package com.example.adapt.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class TTSManager(context: Context, private val onStart: () -> Unit, private val onDone: () -> Unit) {

    private var tts: TextToSpeech? = null
    private var currentLang = Locale.ENGLISH

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = currentLang
            }
        }

        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = onStart()
            override fun onDone(utteranceId: String?) = onDone()
            override fun onError(utteranceId: String?) {
                Log.e("TTS", "Error in TTS")
            }
        })
    }

    fun updateLanguage(langCode: String) {
        currentLang = Locale.forLanguageTag(langCode)
        tts?.language = currentLang
    }

    fun speak(text: String, langCode: String) {
        updateLanguage(langCode)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
