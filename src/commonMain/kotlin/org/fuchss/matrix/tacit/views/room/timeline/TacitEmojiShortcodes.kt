package org.fuchss.matrix.tacit.views.room.timeline

internal data class TacitEmojiShortcode(
    val shortcode: String,
    val emoji: String,
)

private val customEmojis = listOf(
    TacitEmojiShortcode("joy", "\uD83D\uDE02"),
    TacitEmojiShortcode("grin", "\uD83D\uDE00"),
    TacitEmojiShortcode("smiley", "\uD83D\uDE03"),
    TacitEmojiShortcode("smile", "\uD83D\uDE04"),
    TacitEmojiShortcode("sweat_smile", "\uD83D\uDE05"),
    TacitEmojiShortcode("laughing", "\uD83D\uDE06"),
    TacitEmojiShortcode("wink", "\uD83D\uDE09"),
    TacitEmojiShortcode("blush", "\uD83D\uDE0A"),
    TacitEmojiShortcode("yum", "\uD83D\uDE0B"),
    TacitEmojiShortcode("sunglasses", "\uD83D\uDE0E"),
    TacitEmojiShortcode("heart_eyes", "\uD83D\uDE0D"),
    TacitEmojiShortcode("kissing_heart", "\uD83D\uDE18"),
    TacitEmojiShortcode("thinking", "\uD83E\uDD14"),
    TacitEmojiShortcode("neutral", "\uD83D\uDE10"),
    TacitEmojiShortcode("expressionless", "\uD83D\uDE11"),
    TacitEmojiShortcode("smirk", "\uD83D\uDE0F"),
    TacitEmojiShortcode("rolling_eyes", "\uD83D\uDE44"),
    TacitEmojiShortcode("cry", "\uD83D\uDE22"),
    TacitEmojiShortcode("sob", "\uD83D\uDE2D"),
    TacitEmojiShortcode("angry", "\uD83D\uDE20"),
    TacitEmojiShortcode("rage", "\uD83D\uDE21"),
    TacitEmojiShortcode("sleeping", "\uD83D\uDE34"),
    TacitEmojiShortcode("thumbsup", "\uD83D\uDC4D"),
    TacitEmojiShortcode("thumbsdown", "\uD83D\uDC4E"),
    TacitEmojiShortcode("clap", "\uD83D\uDC4F"),
    TacitEmojiShortcode("wave", "\uD83D\uDC4B"),
    TacitEmojiShortcode("ok_hand", "\uD83D\uDC4C"),
    TacitEmojiShortcode("muscle", "\uD83D\uDCAA"),
    TacitEmojiShortcode("pray", "\uD83D\uDE4F"),
    TacitEmojiShortcode("fire", "\uD83D\uDD25"),
    TacitEmojiShortcode("rocket", "\uD83D\uDE80"),
    TacitEmojiShortcode("tada", "\uD83C\uDF89"),
    TacitEmojiShortcode("party_popper", "\uD83C\uDF89"),
    TacitEmojiShortcode("sparkles", "\u2728"),
    TacitEmojiShortcode("star", "\u2B50"),
    TacitEmojiShortcode("heart", "\u2764\uFE0F"),
    TacitEmojiShortcode("red_heart", "\u2764\uFE0F"),
    TacitEmojiShortcode("yellow_heart", "\uD83D\uDC9B"),
    TacitEmojiShortcode("green_heart", "\uD83D\uDC9A"),
    TacitEmojiShortcode("blue_heart", "\uD83D\uDC99"),
    TacitEmojiShortcode("purple_heart", "\uD83D\uDC9C"),
    TacitEmojiShortcode("white_heart", "\uD83E\uDD0D"),
    TacitEmojiShortcode("black_heart", "\uD83D\uDDA4"),
    TacitEmojiShortcode("broken_heart", "\uD83D\uDC94"),
    TacitEmojiShortcode("eyes", "\uD83D\uDC40"),
    TacitEmojiShortcode("100", "\uD83D\uDCAF"),
    TacitEmojiShortcode("check", "\u2705"),
    TacitEmojiShortcode("x", "\u274C"),
    TacitEmojiShortcode("warning", "\u26A0\uFE0F"),
    TacitEmojiShortcode("poop", "\uD83D\uDCA9"),
    TacitEmojiShortcode("ghost", "\uD83D\uDC7B"),
    TacitEmojiShortcode("robot", "\uD83E\uDD16"),
    TacitEmojiShortcode("skull", "\uD83D\uDC80"),
    TacitEmojiShortcode("coffee", "\u2615"),
    TacitEmojiShortcode("tea", "\uD83C\uDF75"),
    TacitEmojiShortcode("beer", "\uD83C\uDF7A"),
    TacitEmojiShortcode("pizza", "\uD83C\uDF55"),
    TacitEmojiShortcode("burger", "\uD83C\uDF54"),
    TacitEmojiShortcode("cake", "\uD83C\uDF82"),
    TacitEmojiShortcode("gift", "\uD83C\uDF81"),
    TacitEmojiShortcode("camera", "\uD83D\uDCF7"),
    TacitEmojiShortcode("phone", "\uD83D\uDCF1"),
    TacitEmojiShortcode("computer", "\uD83D\uDCBB"),
    TacitEmojiShortcode("lock", "\uD83D\uDD12"),
    TacitEmojiShortcode("key", "\uD83D\uDD11"),
    TacitEmojiShortcode("mail", "\u2709\uFE0F"),
    TacitEmojiShortcode("bell", "\uD83D\uDD14"),
    TacitEmojiShortcode("zzz", "\uD83D\uDCA4"),
    TacitEmojiShortcode("pleading_face", "\uD83E\uDD7A"),
    TacitEmojiShortcode("face_holding_back_tears", "\uD83E\uDD79"),
    TacitEmojiShortcode("o", "\u2B55"),
)

private val platformEmojis =
    loadPlatformEmojiShortcodes().map { TacitEmojiShortcode(it.shortcode.replace(" ", "_"), it.emoji) }

private val allEmojis = (customEmojis + platformEmojis).distinctBy { it.shortcode }.sortedBy { it.shortcode }
    .sortedBy { it.shortcode.length }

internal fun emojiShortcodeSuggestions(
    query: String,
    limit: Int = 8,
): List<TacitEmojiShortcode> {
    if (query.isBlank()) return emptyList()
    val normalizedQuery = query.lowercase()
    val startsWith = allEmojis.asSequence()
        .filter { it.shortcode.startsWith(normalizedQuery) }
        .take(limit)
        .toList()
    val contains = allEmojis.asSequence()
        .filter { it.shortcode.contains(normalizedQuery) }
        .take(limit)
        .toList()
    return (startsWith + contains).distinctBy { it.shortcode }
        .take(limit)
        .toList()
}

internal expect fun loadPlatformEmojiShortcodes(): List<TacitEmojiShortcode>
