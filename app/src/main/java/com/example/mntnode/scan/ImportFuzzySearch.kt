package com.example.mntnode.scan

import kotlin.math.min

/**
 * Fuzzy filename matching for the import list: substring, subsequence (gaps allowed),
 * multi-word AND, and light Levenshtein tolerance on word segments.
 */
internal object ImportFuzzySearch {

    /**
     * Text inside double quotes must appear as a contiguous substring (case-insensitive), with no
     * fuzzy/subsequence/Levenshtein behavior. Unquoted parts use the usual fuzzy matching.
     */
    fun matches(displayName: String, queryRaw: String): Boolean {
        val text = displayName.lowercase()
        val trimmed = queryRaw.trim()
        if (trimmed.isEmpty()) return true

        val (exactPhrases, fuzzyRemainder) = extractQuotedPhrases(trimmed)
        for (p in exactPhrases) {
            if (!text.contains(p)) return false
        }
        if (fuzzyRemainder.isBlank()) return true
        return matchesFuzzyOnly(text, fuzzyRemainder)
    }

    private fun extractQuotedPhrases(queryRaw: String): Pair<List<String>, String> {
        val exact = mutableListOf<String>()
        val regex = Regex("\"([^\"]*)\"")
        regex.findAll(queryRaw).forEach { m ->
            val inner = m.groupValues[1].trim()
            if (inner.isNotEmpty()) exact.add(inner.lowercase())
        }
        val remainder = queryRaw.replace(regex, " ").trim().replace(Regex("\\s+"), " ")
        return exact to remainder
    }

    private fun matchesFuzzyOnly(text: String, queryRaw: String): Boolean {
        val q = queryRaw.trim().lowercase()
        if (q.isEmpty()) return true
        if (text.contains(q)) return true

        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (tokens.size <= 1) {
            tokenMatches(text, tokens.firstOrNull() ?: q)
        } else {
            tokens.all { tokenMatches(text, it) }
        }
    }

    private fun tokenMatches(textLower: String, token: String): Boolean {
        if (token.isEmpty()) return true
        if (textLower.contains(token)) return true
        if (subsequenceMatch(token, textLower)) return true
        if (token.length < 2) return false

        val words = textLower.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
        val maxDist = maxEditsAllowed(token.length)
        return words.any { w -> levenshteinBounded(token, w, maxDist) }
    }

    /** All characters of [q] appear in order within [t]. */
    private fun subsequenceMatch(q: String, t: String): Boolean {
        var qi = 0
        var ti = 0
        while (qi < q.length && ti < t.length) {
            if (q[qi] == t[ti]) qi++
            ti++
        }
        return qi == q.length
    }

    private fun maxEditsAllowed(len: Int): Int = when {
        len <= 3 -> 1
        len <= 6 -> 2
        else -> min(3, (len + 2) / 4)
    }

    /**
     * True if Levenshtein distance is at most [max], with early exit when impossible.
     */
    private fun levenshteinBounded(a: String, b: String, max: Int): Boolean {
        if (max < 0) return false
        if (a == b) return true
        var s = a
        var t = b
        if (s.length > t.length) {
            s = t.also { t = s }
        }
        val n = s.length
        val m = t.length
        if (m - n > max) return false

        var row = IntArray(n + 1) { it }
        for (j in 1..m) {
            var prevDiag = row[0]
            row[0] = j
            var rowMin = row[0]
            for (i in 1..n) {
                val temp = row[i]
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                row[i] = minOf(row[i] + 1, row[i - 1] + 1, prevDiag + cost)
                prevDiag = temp
                if (row[i] < rowMin) rowMin = row[i]
            }
            if (rowMin > max) return false
        }
        return row[n] <= max
    }
}
