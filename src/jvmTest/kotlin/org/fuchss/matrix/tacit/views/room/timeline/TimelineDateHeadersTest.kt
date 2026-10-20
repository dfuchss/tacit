package org.fuchss.matrix.tacit.views.room.timeline

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineDateHeadersTest {
    private data class Item(val key: String, val date: String?, val empty: Boolean = false)

    /** [itemsOldestFirst] for readability; the function works on newest-first lists like the timeline. */
    private fun render(vararg itemsOldestFirst: Item): List<String> =
        insertDateHeaders(
            itemsNewestFirst = itemsOldestFirst.toList().asReversed(),
            dateOf = { it.date },
            rendersNothing = { it.empty },
            header = { "date-${it.key}" },
            element = { it.key },
        ).asReversed()

    @Test
    fun `a header above the first element of every date`() {
        assertEquals(
            listOf("date-a", "a", "b", "date-c", "c"),
            render(Item("a", "Mon"), Item("b", "Mon"), Item("c", "Tue")),
        )
    }

    @Test
    fun `result is newest first`() {
        val result = insertDateHeaders(listOf("b", "a"), { "Mon" }, { false }, { "date-$it" }, { it })
        assertEquals(listOf("b", "a", "date-a"), result)
    }

    @Test
    fun `elements that render nothing get no header and do not end a date`() {
        assertEquals(
            listOf("date-a", "a", "e", "date-c", "c"),
            render(Item("a", "Mon"), Item("e", "Tue", empty = true), Item("c", "Tue")),
        )
    }

    @Test
    fun `an element that renders nothing but continues the date stays plain`() {
        assertEquals(
            listOf("e", "date-a", "a"),
            render(Item("e", "Mon", empty = true), Item("a", "Mon")),
        )
    }

    @Test
    fun `elements without a date at the start get no header`() {
        assertEquals(listOf("a", "date-b", "b"), render(Item("a", null), Item("b", "Mon")))
    }
}
