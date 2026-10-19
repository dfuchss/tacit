package org.fuchss.matrix.tacit.viewmodel.room.timeline

import org.fuchss.matrix.tacit.views.room.timeline.emojiShortcodeSuggestions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TacitComposerTextTest {
    private fun shortcode(text: String, cursor: Int = text.length) = findEmojiShortcodeMatch(text, cursor, cursor)

    private fun slash(text: String, cursor: Int = text.length) = findSlashCommandMatch(text, cursor, cursor)

    @Test
    fun shortcodeAtEndOfText() {
        assertEquals(TacitEmojiShortcodeMatch(6, 10, "joy"), shortcode("hello :joy"))
    }

    @Test
    fun shortcodeQueryIsLowerCased() {
        assertEquals("joy", shortcode(":JoY")?.query)
    }

    @Test
    fun shortcodeUsesTextBeforeCursorOnly() {
        assertEquals(TacitEmojiShortcodeMatch(0, 3, "jo"), shortcode(":joy tail", cursor = 3))
    }

    @Test
    fun shortcodeNeedsCollapsedSelection() {
        assertNull(findEmojiShortcodeMatch(":joy", 1, 4))
    }

    @Test
    fun shortcodeNeedsNonBlankPartialToken() {
        assertNull(shortcode(":"))
        assertNull(shortcode("a :"))
        assertNull(shortcode(":joy", cursor = 0))
    }

    @Test
    fun shortcodeRejectsColonAfterWordCharacter() {
        assertNull(shortcode("12:30"))
        assertNull(shortcode("a_:joy"))
        assertNull(shortcode("a-:joy"))
    }

    @Test
    fun shortcodeRejectsClosedOrInvalidTokens() {
        assertNull(shortcode(":joy:"))
        assertNull(shortcode(":jo y"))
        assertNull(shortcode(":jo!"))
    }

    @Test
    fun shortcodeAllowsUnderscoreAndDash() {
        assertEquals("sweat_smile", shortcode(":sweat_smile")?.query)
        assertEquals("a-b", shortcode("x :a-b")?.query)
    }

    @Test
    fun replaceShortcodeAtEndAppendsSpace() {
        val match = shortcode("hi :joy")!!
        assertEquals(TacitComposerEdit("hi X ", 5), replaceEmojiShortcode("hi :joy", match, "X"))
    }

    @Test
    fun replaceShortcodeInMiddleKeepsTail() {
        val match = shortcode(":joy tail", cursor = 4)!!
        assertEquals(TacitComposerEdit("X tail", 1), replaceEmojiShortcode(":joy tail", match, "X"))
    }

    @Test
    fun emojiSuggestionsPrefixAndCaseInsensitive() {
        val suggestions = emojiShortcodeSuggestions("JOY")
        assertTrue(suggestions.isNotEmpty())
        assertEquals("joy", suggestions.first().shortcode)
    }

    @Test
    fun emojiSuggestionsNoMatchOrBlankQuery() {
        assertTrue(emojiShortcodeSuggestions("zzzzqqqq").isEmpty())
        assertTrue(emojiShortcodeSuggestions("  ").isEmpty())
        assertTrue(emojiShortcodeSuggestions("joy", limit = 0).isEmpty())
    }

    @Test
    fun emojiSuggestionsRespectLimit() {
        assertTrue(emojiShortcodeSuggestions("s", limit = 3).size <= 3)
    }

    @Test
    fun slashMatchOnlyAtStartOfText() {
        assertEquals(TacitSlashCommandMatch(0, 4, "spo"), slash("/spo"))
        assertNull(slash("hi /spo"))
        assertNull(slash("/spo\nx"))
    }

    @Test
    fun slashMatchStopsAfterArguments() {
        assertNull(slash("/spoiler text"))
        assertNull(slash("/spoiler\ttext"))
        assertNull(slash("/sp1"))
    }

    @Test
    fun slashMatchCursorPositions() {
        assertNull(slash("/spoiler", cursor = 0))
        assertEquals("sp", slash("/spoiler", cursor = 3)?.query)
        assertEquals("", slash("/")?.query)
        assertNull(findSlashCommandMatch("/spoiler", 1, 3))
    }

    @Test
    fun slashMatchLowerCasesQuery() {
        assertEquals("spoiler", slash("/SPOILER")?.query)
    }

    @Test
    fun slashSuggestionsPrefixBeforeContains() {
        assertEquals(listOf("spoiler"), slashCommandSuggestions("spo").map { it.command })
        assertEquals("me", slashCommandSuggestions("ME").first().command)
        assertTrue(slashCommandSuggestions("flip").map { it.command }.containsAll(listOf("tableflip", "unflip")))
    }

    @Test
    fun slashSuggestionsBlankQueryListsAllAndNoMatchIsEmpty() {
        assertEquals(tacitSlashCommands, slashCommandSuggestions(""))
        assertTrue(slashCommandSuggestions("zzz").isEmpty())
        assertEquals(2, slashCommandSuggestions("", limit = 2).size)
    }

    @Test
    fun replaceSlashCommandKeepsTextAfterCursor() {
        val match = slash("/spo rest", cursor = 4)!!
        assertEquals(TacitComposerEdit("/spoiler  rest", 9), replaceSlashCommand("/spo rest", match, "/spoiler "))
    }

    @Test
    fun parseNotACommand() {
        assertIs<TacitSlashCommandParseResult.NotACommand>(parseSlashCommand("hello"))
        assertIs<TacitSlashCommandParseResult.NotACommand>(parseSlashCommand("/"))
        assertIs<TacitSlashCommandParseResult.NotACommand>(parseSlashCommand("/ spoiler x"))
    }

    @Test
    fun parseUnknownCommandKeepsOriginalSpelling() {
        assertEquals(TacitSlashCommandParseResult.Unknown("Nope"), parseSlashCommand("/Nope with args"))
    }

    @Test
    fun parseCommandWithArguments() {
        val result = assertIs<TacitSlashCommandParseResult.Parsed>(parseSlashCommand("/Spoiler why | secret"))
        assertEquals(TacitSlashCommandId.SPOILER, result.command.id)
        assertEquals("why | secret", result.rawArguments)
    }

    @Test
    fun parseArgumentRequiringCommandWithoutArgumentsIsIncomplete() {
        for (text in listOf("/spoiler", "/spoiler ", "/me", "/notice   ")) {
            assertIs<TacitSlashCommandParseResult.Incomplete>(parseSlashCommand(text), text)
        }
    }

    @Test
    fun parseOptionalArgumentCommandWithoutArguments() {
        val result = assertIs<TacitSlashCommandParseResult.Parsed>(parseSlashCommand("/shrug"))
        assertEquals(TacitSlashCommandId.SHRUG, result.command.id)
        assertEquals("", result.rawArguments)
    }

    @Test
    fun parseKeepsMultilineArguments() {
        val result = assertIs<TacitSlashCommandParseResult.Parsed>(parseSlashCommand("/me waves\nand more"))
        assertEquals("waves\nand more", result.rawArguments)
    }

    @Test
    fun spoilerContentWithAndWithoutReason() {
        assertEquals(TacitSpoilerContent("why", "secret"), parseSpoilerContent(" why | secret "))
        assertEquals(TacitSpoilerContent(null, "secret"), parseSpoilerContent(" secret "))
        assertEquals(TacitSpoilerContent(null, "secret"), parseSpoilerContent(" | secret"))
        assertEquals(TacitSpoilerContent("why", ""), parseSpoilerContent("why |"))
    }
}
