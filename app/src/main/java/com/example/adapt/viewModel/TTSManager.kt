package com.example.adapt.viewModel

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*

class TTSManager(
    private val context: Context,
    private val tts: TextToSpeech,
    private val onSpeakStart: () -> Unit,
    private val onSpeakEnd: () -> Unit
) {

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakStart()
            }

            override fun onDone(utteranceId: String?) {
                onSpeakEnd()
            }

            override fun onError(utteranceId: String?) {
                onSpeakEnd()
            }
        })
    }

    fun speak(text: String, language: String) {
        if (tts.isSpeaking) {
            tts.stop()
        }

        val processedText = text.replace(". ", "... ")
            .replace("? ", "... ")
            .replace("! ", "... ")

        val pitch = 1.0f + (Random().nextFloat() * 0.1f - 0.05f)
        val rate = if (language == "ta-IN") 0.85f else 0.95f

        tts.setPitch(pitch)
        tts.setSpeechRate(rate)

        val utteranceId = "UTTER_${System.currentTimeMillis()}"
        tts.speak(processedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun updateLanguage(langCode: String) {
        val locale = when (langCode) {
            "ta-IN" -> Locale("ta", "IN")
            else -> Locale.US
        }

        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("TTSManager", "Language not supported: $langCode")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}