package org.fuchss.matrix.tacit.views.room.timeline

import com.vdurmont.emoji.EmojiManager

internal actual fun loadPlatformEmojiShortcodes(): List<TacitEmojiShortcode> {
    val result = mutableListOf<TacitEmojiShortcode>()
    EmojiManager.getAll().forEach { emoji ->
        val unicode = emoji.unicode ?: return@forEach
        emoji.aliases.orEmpty().forEach { alias ->
            if (alias.isNotBlank()) {
                result.add(TacitEmojiShortcode(shortcode = alias, emoji = unicode))
            }
        }
    }
    return result
}
