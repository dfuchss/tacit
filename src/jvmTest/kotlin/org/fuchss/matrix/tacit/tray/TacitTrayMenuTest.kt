package org.fuchss.matrix.tacit.tray

import org.freedesktop.dbus.types.Variant
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TacitTrayMenuTest {
    private val entries = listOf(
        TacitTrayMenuEntry.Item("Open", {}),
        TacitTrayMenuEntry.Item("Restart to update", {}),
        TacitTrayMenuEntry.Separator,
        TacitTrayMenuEntry.Item("Exit", {}),
    )
    private val menu = TacitDbusMenu(entries, revision = 7)

    @Test
    fun rootLayoutListsTheEntriesInOrderWithIdsFromOne() {
        val root = assertNotNullLayout(menu.layout(parentId = 0, recursionDepth = -1, propertyNames = emptyList()))
        assertEquals(0, root.id)
        assertEquals("submenu", root.properties["children-display"])
        assertEquals(listOf(1, 2, 3, 4), root.children.map { it.id })
        assertEquals(
            listOf("Open", "Restart to update", null, "Exit"),
            root.children.map { it.properties["label"] },
        )
        assertEquals("separator", root.children[2].properties["type"])
        assertTrue(root.children.all { it.children.isEmpty() })
    }

    @Test
    fun depthZeroReturnsOnlyTheParent() {
        val root = assertNotNullLayout(menu.layout(parentId = 0, recursionDepth = 0, propertyNames = emptyList()))
        assertTrue(root.children.isEmpty())
    }

    @Test
    fun anItemCanBeRequestedOnItsOwn() {
        val item = assertNotNullLayout(menu.layout(parentId = 4, recursionDepth = -1, propertyNames = emptyList()))
        assertEquals(4, item.id)
        assertEquals("Exit", item.properties["label"])
        assertTrue(item.children.isEmpty())
    }

    @Test
    fun unknownIdsHaveNoLayoutOrProperties() {
        assertNull(menu.layout(parentId = 5, recursionDepth = -1, propertyNames = emptyList()))
        assertNull(menu.properties(-1))
        assertNull(menu.entry(0))
        assertTrue(menu.contains(0))
        assertTrue(menu.contains(4))
        assertTrue(!menu.contains(5))
    }

    @Test
    fun propertyNamesRestrictTheReturnedProperties() {
        assertEquals(mapOf("label" to "Open"), menu.properties(1, listOf("label")))
        val root = assertNotNullLayout(menu.layout(0, -1, listOf("type")))
        assertEquals(listOf(emptyMap(), emptyMap(), mapOf("type" to "separator"), emptyMap()), root.children.map { it.properties })
    }

    @Test
    fun underscoresAreNotTakenAsMnemonics() {
        assertEquals("a__b", escapeDbusMenuLabel("a_b"))
        val menu = TacitDbusMenu(listOf(TacitTrayMenuEntry.Item("Tacit_dev", {})), revision = 1)
        assertEquals("Tacit__dev", menu.properties(1)?.get("label"))
    }

    @Test
    fun structureIgnoresTheClickHandlers() {
        val again = entries.map { if (it is TacitTrayMenuEntry.Item) it.copy(onClick = {}) else it }
        assertEquals(TacitDbusMenu.structure(entries), TacitDbusMenu.structure(again))
        assertNotEquals(TacitDbusMenu.structure(entries), TacitDbusMenu.structure(entries.drop(1)))
    }

    @Test
    fun layoutConvertsToTheDbusmenuStruct() {
        val root = assertNotNullLayout(menu.layout(0, -1, emptyList())).toStruct()
        assertEquals(0, root.id)
        assertEquals(4, root.children.size)
        root.children.forEach { assertEquals(DBUSMENU_LAYOUT_SIGNATURE, it.sig) }
        val exit = root.children[3].value as DbusMenuLayoutItem
        assertEquals(Variant("Exit"), exit.properties["label"])
    }

    @Test
    fun pixmapIsArgbInNetworkByteOrderRowByRow() {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0x80FF0000.toInt())
        image.setRGB(1, 0, 0xFF00FF00.toInt())
        image.setRGB(0, 1, 0xFF0000FF.toInt())
        image.setRGB(1, 1, 0x00000000)
        val expected = byteArrayOf(
            0x80.toByte(), 0xFF.toByte(), 0x00, 0x00,
            0xFF.toByte(), 0x00, 0xFF.toByte(), 0x00,
            0xFF.toByte(), 0x00, 0x00, 0xFF.toByte(),
            0x00, 0x00, 0x00, 0x00,
        )
        assertContentEquals(expected, argb32NetworkOrder(image))
    }

    private fun assertNotNullLayout(node: TacitDbusMenuNode?): TacitDbusMenuNode =
        node ?: throw AssertionError("expected a layout")
}
