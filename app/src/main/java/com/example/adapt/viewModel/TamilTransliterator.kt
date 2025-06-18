package com.example.adapt.viewModel

object TamilTransliterator {
    private val mapping = mapOf(
        "a" to "அ", "aa" to "ஆ", "i" to "இ", "ii" to "ஈ",
        "u" to "உ", "uu" to "ஊ", "e" to "எ", "ee" to "ஏ",
        "ai" to "ஐ", "o" to "ஒ", "oo" to "ஓ", "au" to "ஔ",
        "ka" to "க", "nga" to "ங", "ca" to "ச", "ja" to "ஜ", "nya" to "ஞ",
        "ta" to "ட", "na" to "ண", "tha" to "த", "dha" to "த", "pa" to "ப",
        "ma" to "ம", "ya" to "ய", "ra" to "ர", "la" to "ல", "va" to "வ",
        "zha" to "ழ", "L" to "ள", "R" to "ற", "n" to "ன"
        // This is a minimal example, expand for better accuracy
    )

    fun transliterate(text: String): String {
        var output = text
        mapping.forEach { (latin, tamil) ->
            output = output.replace(Regex("(?i)$latin"), tamil)
        }
        return output
    }
}
