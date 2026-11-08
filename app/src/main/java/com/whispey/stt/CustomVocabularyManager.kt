package com.whispey.stt

import java.util.regex.Pattern
import kotlin.math.max

/**
 * Manages custom vocabulary rules and executes two-stage phonetic & dictionary replacement.
 *
 * Stage A: Explicit sound-alike alias replacement (e.g. "auru kit, oru kit -> Orukeet").
 * Stage B: Automatic phonetic & fuzzy matching for standalone target words >= 5 characters.
 */
object CustomVocabularyManager {

    data class VocabularyRule(
        val target: String,
        val aliases: List<String> = emptyList()
    ) {
        val isStandalone: Boolean
            get() = aliases.isEmpty()

        fun toEntryString(): String = if (aliases.isEmpty()) {
            target
        } else {
            "${aliases.joinToString(", ")} -> $target"
        }

        companion object {
            fun fromEntryString(line: String): VocabularyRule? {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return null

                return if (trimmed.contains("->")) {
                    val parts = trimmed.split("->", limit = 2)
                    val target = parts[1].trim()
                    val aliasList = parts[0].split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (target.isNotEmpty()) {
                        VocabularyRule(target, aliasList)
                    } else null
                } else {
                    VocabularyRule(trimmed, emptyList())
                }
            }
        }
    }

    /**
     * Applies both Stage A (explicit aliases) and Stage B (automatic fuzzy/phonetic matching)
     * to [text] using [rules].
     */
    fun applyCustomVocabulary(text: String, rules: List<VocabularyRule>): String {
        if (text.isBlank() || rules.isEmpty()) return text

        var result = text

        // ---- Stage A: Explicit Alias Replacement ----
        // Collect all explicit (alias -> target) pairs and sort by alias length descending
        val aliasPairs = rules.flatMap { rule ->
            rule.aliases.map { alias -> alias to rule.target }
        }.sortedByDescending { it.first.length }

        for ((alias, target) in aliasPairs) {
            if (alias.isBlank() || target.isBlank()) continue
            val regex = Regex("""(?i)\b${Regex.escape(alias)}\b""")
            result = regex.replace(result, target)
        }

        // ---- Stage B: Automatic Phonetic / Fuzzy Matching for Standalone Words ----
        val standaloneRules = rules.filter { it.isStandalone }
        for (rule in standaloneRules) {
            val target = rule.target
            val targetClean = target.replace(Regex("""[\s\-]"""), "")
            // Guard: only run automatic fuzzy matching on target vocabulary words >= 5 characters
            if (targetClean.length < 5) continue

            result = matchAndReplaceFuzzy(result, target, targetClean)
        }

        return result
    }

    private fun matchAndReplaceFuzzy(text: String, target: String, targetClean: String): String {
        val targetSkel = phoneticSkeleton(targetClean)
        if (targetSkel.isEmpty()) return text

        val tokens = text.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return text

        val output = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            var bestMatch: String? = null
            var bestScore = -1.0
            var bestK = 0

            // Slide a window of 1, 2, and 3 consecutive words
            for (k in 1..3) {
                if (i + k <= tokens.size) {
                    val windowTokens = tokens.subList(i, i + k)
                    val windowCores = windowTokens.map { extractTokenParts(it).core }
                    val joinedCore = windowCores.joinToString("").replace(Regex("""[\s\-]"""), "")
                    if (joinedCore.isEmpty()) continue

                    val joinedSkel = phoneticSkeleton(joinedCore)
                    // Guard: require first phonetic consonant/vowel group to match
                    if (joinedSkel.isEmpty() || joinedSkel[0] != targetSkel[0]) continue

                    val rawSim = similarity(targetClean, joinedCore)
                    val skelSim = similarity(targetSkel, joinedSkel)
                    val skelDist = levenshteinDistance(targetSkel, joinedSkel)
                    val effectiveSim = maxOf(rawSim, skelSim)

                    // Verify similarity >= 0.80 AND (skelDist <= 1 OR similarity >= 0.82)
                    if (effectiveSim >= 0.80 && (skelDist <= 1 || effectiveSim >= 0.82)) {
                        // Length penalty to prefer exact word boundaries over absorbing extra adjacent words
                        val lenDiff = Math.abs(joinedCore.length - targetClean.length)
                        val score = effectiveSim - (lenDiff * 0.05)
                        if (score > bestScore) {
                            bestScore = score
                            bestK = k
                            val leadPunct = extractTokenParts(windowTokens.first()).leadingPunct
                            val trailPunct = extractTokenParts(windowTokens.last()).trailingPunct
                            bestMatch = leadPunct + target + trailPunct
                        }
                    }
                }
            }

            if (bestMatch != null) {
                output.add(bestMatch)
                i += bestK
            } else {
                output.add(tokens[i])
                i += 1
            }
        }

        return output.joinToString(" ")
    }

    private data class TokenParts(val leadingPunct: String, val core: String, val trailingPunct: String)

    private fun extractTokenParts(token: String): TokenParts {
        var start = 0
        while (start < token.length && !token[start].isLetterOrDigit()) {
            start++
        }
        var end = token.length
        while (end > start && !token[end - 1].isLetterOrDigit()) {
            end--
        }
        val leading = token.substring(0, start)
        val core = if (start < end) token.substring(start, end) else ""
        val trailing = token.substring(end)
        return TokenParts(leading, core, trailing)
    }

    /**
     * Normalizes common English/Italian phonetic equivalents:
     * - Maps: ph->f, ck/k/qu/ch->c, z->s, y->i, w->v, oo/ou->u, ee/ea->i
     * - Collapses consecutive duplicate characters
     */
    fun phoneticSkeleton(word: String): String {
        var s = word.lowercase()
        // Multi-char mappings
        s = s.replace("ph", "f")
        s = s.replace("ck", "c")
        s = s.replace("ch", "c")
        s = s.replace("qu", "c")
        s = s.replace("oo", "u")
        s = s.replace("ou", "u")
        s = s.replace("ee", "i")
        s = s.replace("ea", "i")

        // Single-char mappings
        s = s.replace("k", "c")
        s = s.replace("z", "s")
        s = s.replace("y", "i")
        s = s.replace("w", "v")

        // Collapse double letters
        val sb = StringBuilder(s.length)
        for (i in s.indices) {
            val c = s[i]
            if (sb.isEmpty() || sb[sb.length - 1] != c) {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = IntArray(n + 1) { it }

        for (i in 1..m) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..n) {
                val temp = dp[j]
                val cost = if (s1[i - 1].lowercaseChar() == s2[j - 1].lowercaseChar()) 0 else 1
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + cost)
                prev = temp
            }
        }
        return dp[n]
    }

    fun similarity(s1: String, s2: String): Double {
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(s1, s2)
        return 1.0 - (dist.toDouble() / maxLen)
    }
}
