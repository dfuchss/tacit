package org.fuchss.matrix.tacit.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TacitVersionTest {
    @Test
    fun parsesReleaseVersionsAndTags() {
        assertEquals(TacitVersion(26, 9, 2, 1), TacitVersion.parse("26.9.2-tacit.1"))
        assertEquals(TacitVersion(26, 9, 2, 1), TacitVersion.parse("v26.9.2-tacit.1"))
        assertEquals("26.10.0-tacit.12", TacitVersion.parse("26.10.0-tacit.12").toString())
    }

    @Test
    fun nonReleaseVersionsDisableTheUpdater() {
        assertNull(TacitVersion.parse("26.9.2-SNAPSHOT.LOCAL"))
        assertNull(TacitVersion.parse("26.9.2-SNAPSHOT"))
        assertNull(TacitVersion.parse("26.9.2"))
        assertNull(TacitVersion.parse("26.9.2-tacit"))
        assertNull(TacitVersion.parse("26.9.2-tacit.1-dirty"))
        assertNull(TacitVersion.parse(""))
    }

    @Test
    fun comparesAllFourNumbersNumerically() {
        fun v(s: String) = checkNotNull(TacitVersion.parse(s))
        assertTrue(v("26.10.0-tacit.1") > v("26.9.2-tacit.7"))
        assertTrue(v("26.9.2-tacit.10") > v("26.9.2-tacit.9"))
        assertTrue(v("26.9.3-tacit.1") > v("26.9.2-tacit.9"))
        assertTrue(v("27.0.0-tacit.1") > v("26.99.99-tacit.99"))
        assertEquals(0, v("26.9.2-tacit.2").compareTo(v("v26.9.2-tacit.2")))
    }
}
