import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content

object GeminiHandler {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = "AIzaSyAvGgefaf8qo59mK_s7cDKvobS_1cSpKgY"
    )

    suspend fun initGeminiAI(language: String): String {
        val chat = generativeModel.startChat()
        val greeting = if (language == "ta-IN") {
            "நான் டி-போட், உங்கள் நண்பர். அடாப்ட் ரோபோடிக்ஸ் நிறுவனம் உருவாக்கியது. என்னை பற்றி கேட்டதற்கு நன்றி!"
        } else {
            "I am tBot, your friendly assistant created by Adapt Robotics. Thanks for asking about me!"
        }
        val response = chat.sendMessage(greeting)
        return response.text?.trim() ?: fallback(language)
    }

    suspend fun sendMessage(prompt: String, language: String, history: List<Pair<String, String>>): String {
        return try {
            val historyContent = history.map {
                content(it.first) { text(it.second) }
            }

            val chat = generativeModel.startChat(history = historyContent)

            val modifiedPrompt = if (language == "ta-IN") {
                    "இந்த கேள்விக்கு சுருக்கமாக, நண்பருக்கே பதிலளிப்பது போல, எமோஜி இல்லாமல் தமிழில் பதில்:\n$prompt"
                } else {
                    "Answer this in a short, friendly, and natural way—no emojis please.:\n$prompt"
                }


            val response = chat.sendMessage(modifiedPrompt)
            response.text?.trim() ?: fallback(language)

        } catch (e: Exception) {
            e.printStackTrace()
            fallback(language)
        }
    }

    private fun fallback(language: String): String {
        return if (language == "ta-IN") "மன்னிக்கவும், பதிலை உருவாக்க முடியவில்லை."
        else "Sorry, I couldn't generate a response."
    }
}
