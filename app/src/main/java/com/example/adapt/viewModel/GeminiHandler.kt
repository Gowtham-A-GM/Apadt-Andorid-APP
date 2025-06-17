package com.example.adapt.viewModel

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiHandler {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = "AIzaSyAvGgefaf8qo59mK_s7cDKvobS_1cSpKgY" // Replace with your actual Gemini API key
    )

    suspend fun getResponse(input: String, language: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = when (language) {
                "ta-IN" -> """
                    நான் டி-போட், உங்கள் நண்பர். அடாப்ட் ரோபோடிக்ஸால் உருவாக்கப்பட்டேன்.
                    இந்த கேள்விக்கு ஒரு நெருக்கமான நண்பர் போல் உரையாடும் முறையில் பதிலளிக்கவும்: $input
                    பதில் சுருக்கமாகவும், உரையாடல் போன்றும் இருக்க வேண்டும். வார்த்தைகளை அதிகம் பயன்படுத்த வேண்டாம். எமோஜிகளைப் பயன்படுத்த வேண்டாம்.
                """.trimIndent()

                else -> """
                    I am tBot, your friendly assistant from Adapt Robotics.
                    Respond to this question in a warm, conversational tone: $input
                    Keep it short, human-like, and avoid emojis. Answer in about 3 sentences max.
                """.trimIndent()
            }

            val response: GenerateContentResponse = generativeModel.generateContent(prompt)
            return@withContext response.text?.trim() ?: fallback(language)

        } catch (e: Exception) {
            Log.e("GeminiHandler", "Error generating response: ${e.message}", e)
            return@withContext fallback(language)
        }
    }

    private fun fallback(language: String): String {
        return if (language == "ta-IN") {
            "மன்னிக்கவும், பதிலை உருவாக்க முடியவில்லை."
        } else {
            "Sorry, I couldn't generate a response."
        }
    }
}