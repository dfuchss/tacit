package org.fuchss.matrix.tacit.viewmodel.util

import kotlin.test.Test
import kotlin.test.assertEquals

class InitialsTest {
    @Test
    fun `blank names give a question mark`() {
        assertEquals("?", initialsOf(""))
        assertEquals("?", initialsOf("   \t\n"))
    }

    @Test
    fun `single word uses the requested length`() {
        assertEquals("AL", initialsOf("alice"))
        assertEquals("A", initialsOf("alice", singleWordLength = 1))
        assertEquals("X", initialsOf("x"))
    }

    @Test
    fun `two or more words use the first letter of the first two`() {
        assertEquals("AB", initialsOf("alice bob"))
        assertEquals("AB", initialsOf("alice bob carol"))
        assertEquals("AB", initialsOf("alice bob", singleWordLength = 1))
    }

    @Test
    fun `surrounding and repeated whitespace is ignored`() {
        assertEquals("AB", initialsOf("  alice \t  bob\n"))
        assertEquals("AL", initialsOf("  alice  "))
    }

    @Test
    fun `surrogate pairs are not split`() {
        assertEquals("😀", initialsOf("😀", singleWordLength = 1))
        assertEquals("😀A", initialsOf("😀 alice"))
        assertEquals("😀😁", initialsOf("😀😁😂"))
    }
}
