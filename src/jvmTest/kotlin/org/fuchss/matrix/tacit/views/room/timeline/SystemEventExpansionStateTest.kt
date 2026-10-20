package org.fuchss.matrix.tacit.views.room.timeline

import kotlin.test.Test
import kotlin.test.assertEquals

class SystemEventExpansionStateTest {
    private fun groups(vararg groups: Set<String>): (String) -> Set<String>? = { key -> groups.firstOrNull { key in it } }

    @Test
    fun `growing group stays expanded and takes in the newcomer`() {
        val result = expandedMemberKeys(setOf("a", "b"), groups(setOf("a", "b", "c")))
        assertEquals(setOf("a", "b", "c"), result)
    }

    @Test
    fun `merged group is expanded when either part was`() {
        val result = expandedMemberKeys(setOf("a", "b"), groups(setOf("a", "b", "c", "d")))
        assertEquals(setOf("a", "b", "c", "d"), result)
    }

    @Test
    fun `split group keeps every part expanded`() {
        val result = expandedMemberKeys(setOf("a", "b", "c", "d"), groups(setOf("a", "b"), setOf("c", "d")))
        assertEquals(setOf("a", "b", "c", "d"), result)
    }

    @Test
    fun `events that left their group are dropped`() {
        val result = expandedMemberKeys(setOf("a", "b", "gone"), groups(setOf("a", "b"), setOf("x", "y")))
        assertEquals(setOf("a", "b"), result)
    }

    @Test
    fun `collapsed groups stay collapsed`() {
        assertEquals(emptySet(), expandedMemberKeys(emptySet(), groups(setOf("a", "b"))))
    }
}
