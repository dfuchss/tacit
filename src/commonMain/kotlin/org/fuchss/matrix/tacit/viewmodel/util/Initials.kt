package org.fuchss.matrix.tacit.viewmodel.util

private val whitespaceRegex = Regex("\\s+")

/**
 * Avatar initials for [name]: first letters of the first two words, or the first [singleWordLength] letters of a
 * single word, upper-cased. `"?"` for a blank name. Letters are whole code points, so emoji are never split.
 */
internal fun initialsOf(name: String, singleWordLength: Int = 2): String {
    val parts = name.split(whitespaceRegex).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().takeCodePoints(singleWordLength).uppercase()
        else -> (parts[0].takeCodePoints(1) + parts[1].takeCodePoints(1)).uppercase()
    }
}

private fun String.takeCodePoints(count: Int): String {
    var end = 0
    repeat(count) {
        if (end < length) {
            end += if (this[end].isHighSurrogate() && end + 1 < length && this[end + 1].isLowSurrogate()) 2 else 1
        }
    }
    return substring(0, end)
}
