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

/** Replaces the matched token with [emoji]; a trailing space is added when the cursor is at the end of the text. */
internal fun replaceEmojiShortcode(
    text: String,
    match: TacitEmojiShortcodeMatch,
    emoji: String,
): TacitComposerEdit {
    val appendSpace = match.cursorIndex >= text.length
    val replacement = if (appendSpace) "$emoji " else emoji
    val newText = text.substring(0, match.startIndex) + replacement + text.substring(match.cursorIndex)
    return TacitComposerEdit(newText, match.startIndex + replacement.length)
}

/** Replaces everything up to the cursor (the `/command` token) with [replacement]. */
internal fun replaceSlashCommand(
    text: String,
    match: TacitSlashCommandMatch,
    replacement: String,
): TacitComposerEdit = TacitComposerEdit(replacement + text.substring(match.cursorIndex), replacement.length)
