package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The completion glue between Tacit's composer helpers and upstream's [TextFieldState]-based input. */
class TacitInputAreaCompletionTest {
    private fun state(text: String, cursor: Int = text.length) = TextFieldState(text, TextRange(cursor))

    @Test
    fun shortcodeCompletionReplacesTheWholeWordAndMovesTheCursor() {
        val state = state("a :sweat_smile b", cursor = 6)
        val match = state.findShortcodeMatch()!!
        state.replaceShortcode(match, "X")
        assertEquals("a X b", state.text.toString())
        assertEquals(TextRange(3), state.selection)
    }

    @Test
    fun shortcodeCompletionAtTheEndAddsASpace() {
        val state = state("hi :joy")
        state.replaceShortcode(state.findShortcodeMatch()!!, "X")
        assertEquals("hi X ", state.text.toString())
        assertEquals(TextRange(5), state.selection)
    }

    @Test
    fun slashCompletionKeepsTheTextAfterTheCursor() {
        val state = state("/spo rest", cursor = 4)
        state.replaceSlashCommand(state.findSlashCommandMatch()!!, "/spoiler ")
        assertEquals("/spoiler rest", state.text.toString())
        assertEquals(TextRange(8), state.selection)
    }

    @Test
    fun matchesFollowTheSelection() {
        assertNull(TextFieldState(":joy", TextRange(1, 4)).findShortcodeMatch())
        assertNull(state(":joy", cursor = 0).findShortcodeMatch())
        assertNull(state("hello /spo").findSlashCommandMatch())
    }
}
