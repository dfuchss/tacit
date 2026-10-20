package org.fuchss.matrix.tacit.viewmodel.room.timeline

internal data class TacitEmojiShortcodeMatch(
    val startIndex: Int,
    val cursorIndex: Int,
    val query: String,
)

/** The result of applying a suggestion to the composer text: the new text and the new cursor position. */
internal data class TacitComposerEdit(
    val text: String,
    val cursor: Int,
)

/**
 * Finds the `:shortcode` token that ends at the cursor. Only a collapsed, non-leading cursor
 * qualifies; the colon must not follow a word character, and the partial token must be non-blank
 * and made of letters, digits, `_` or `-`. The query is lower-cased.
 */
internal fun findEmojiShortcodeMatch(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
): TacitEmojiShortcodeMatch? {
    if (selectionStart != selectionEnd || selectionStart == 0) return null
    val cursorIndex = selectionStart
    val textBeforeCursor = text.substring(0, cursorIndex)
    val colonIndex = textBeforeCursor.lastIndexOf(':')
    if (colonIndex < 0) return null

    if (colonIndex > 0) {
        val previous = textBeforeCursor[colonIndex - 1]
        if (previous.isLetterOrDigit() || previous == '_' || previous == '-') return null
    }

    val query = textBeforeCursor.substring(colonIndex + 1)
    if (query.isBlank()) return null
    if (query.contains(':')) return null
    if (!query.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return null

    return TacitEmojiShortcodeMatch(
        startIndex = colonIndex,
        cursorIndex = cursorIndex,
        query = query.lowercase(),
    )
}

private fun Char.isTokenChar(): Boolean = isLetterOrDigit() || this == '_' || this == '-'

/** End of the token the cursor at [cursorIndex] sits in: the token characters after the cursor belong to it, too. */
private fun tokenEnd(text: String, cursorIndex: Int): Int {
    var end = cursorIndex
    while (end < text.length && text[end].isTokenChar()) end++
    return end
}

/**
 * Replaces the matched token -- including the rest of it after the cursor (`:jo|y`) -- with [emoji].
 * A trailing space is added when nothing follows the token. The cursor goes after the emoji.
 */
internal fun replaceEmojiShortcode(
    text: String,
    match: TacitEmojiShortcodeMatch,
    emoji: String,
): TacitComposerEdit {
    val rest = text.substring(tokenEnd(text, match.cursorIndex))
    val replacement = if (rest.isEmpty()) "$emoji " else emoji
    return TacitComposerEdit(text.substring(0, match.startIndex) + replacement + rest, match.startIndex + replacement.length)
}

/**
 * Replaces the `/command` token -- including the rest of it after the cursor (`/sp|oiler x`) -- with
 * [replacement]. Its trailing space is dropped when the remaining text already starts with
 * whitespace. The cursor goes after the insertion.
 */
internal fun replaceSlashCommand(
    text: String,
    match: TacitSlashCommandMatch,
    replacement: String,
): TacitComposerEdit {
    val rest = text.substring(tokenEnd(text, match.cursorIndex))
    val insertion = if (rest.firstOrNull()?.isWhitespace() == true) replacement.trimEnd() else replacement
    return TacitComposerEdit(text.substring(0, match.startIndex) + insertion + rest, match.startIndex + insertion.length)
}
