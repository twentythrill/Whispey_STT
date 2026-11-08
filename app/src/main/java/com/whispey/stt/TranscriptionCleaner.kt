package com.whispey.stt

/**
 * Text post-processor that strips standalone vocal hesitation fillers in English and Italian
 * and cleans up punctuation and whitespace artifacts.
 */
object TranscriptionCleaner {

    // Target tokens: um, uh, uhm, umm, ehm, emm, erm, ahm, uhh, ehh, hmm, hm, mm, mmm
    // Case-insensitive with strict word boundaries to avoid affecting words like 'summer' or single letters.
    private val FILLER_REGEX = Regex(
        """(?i)\b(?:um|uh|uhm|umm|ehm|emm|erm|ahm|uhh|ehh|hmm|hm|mm|mmm)\b[,]?\s*"""
    )

    private val LEADING_FILLER_CHECK = Regex(
        """(?i)^\s*(?:um|uh|uhm|umm|ehm|emm|erm|ahm|uhh|ehh|hmm|hm|mm|mmm)\b"""
    )

    private val MULTI_COMMA_REGEX = Regex("""\s*,\s*,+""")
    private val DANGLING_COMMA_REGEX = Regex("""\s*,\s*([.!?]|$)""")
    private val SPACE_BEFORE_PUNCT_REGEX = Regex("""\s+([,.:;!?])""")
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")
    private val SENTENCE_RECAP_REGEX = Regex("""([.!?]\s+)([a-z])""")

    /**
     * Cleans transcribed text by removing standalone vocal hesitation fillers and fixing
     * surrounding punctuation, spacing, and capitalization.
     */
    fun cleanTranscription(text: String): String {
        if (text.isBlank()) return ""

        val leadingFillerStripped = LEADING_FILLER_CHECK.containsMatchIn(text)

        // 1. Strip standalone hesitation filler tokens with optional trailing comma and spaces
        var cleaned = FILLER_REGEX.replace(text, "")

        // 2. Fix multiple consecutive commas and dangling commas before punctuation or end of string
        cleaned = MULTI_COMMA_REGEX.replace(cleaned, ",")
        cleaned = DANGLING_COMMA_REGEX.replace(cleaned, "$1")

        // 3. Fix spaces before punctuation (" ," -> ",", " ." -> ".")
        cleaned = SPACE_BEFORE_PUNCT_REGEX.replace(cleaned, "$1")

        // 4. Collapse multiple consecutive spaces
        cleaned = MULTI_SPACE_REGEX.replace(cleaned, " ").trim()

        if (cleaned.isEmpty()) return ""

        // 5. Re-capitalize the first letter of the sentence if the leading word was stripped
        if (leadingFillerStripped && cleaned[0].isLowerCase()) {
            cleaned = cleaned[0].uppercaseChar() + cleaned.substring(1)
        }

        // 6. Re-capitalize after sentence enders (. ! ?)
        cleaned = SENTENCE_RECAP_REGEX.replace(cleaned) { match ->
            match.groupValues[1] + match.groupValues[2].uppercase()
        }

        return cleaned
    }
}
