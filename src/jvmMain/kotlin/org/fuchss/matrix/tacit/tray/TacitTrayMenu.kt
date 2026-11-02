package org.fuchss.matrix.tacit.tray

import java.awt.image.BufferedImage

/** One entry of the tray menu, shared by the AWT tray (macOS, Windows, Linux fallback) and the dbusmenu. */
internal sealed interface TacitTrayMenuEntry {
    data class Item(val label: String, val onClick: () -> Unit) : TacitTrayMenuEntry

    data object Separator : TacitTrayMenuEntry
}

/** A dbusmenu node: [id] `0` is the (invisible) root, the entries follow with ids from `1`. */
internal data class TacitDbusMenuNode(
    val id: Int,
    val properties: Map<String, Any>,
    val children: List<TacitDbusMenuNode>,
)

/**
 * The dbusmenu view of a flat list of [entries] at layout [revision]. Pure data, so the D-Bus object only converts it
 * into structs and variants. Entry `i` has the id `i + 1`.
 */
internal class TacitDbusMenu(val entries: List<TacitTrayMenuEntry>, val revision: Long) {
    /** The entry with [id], or `null` for the root and unknown ids. */
    fun entry(id: Int): TacitTrayMenuEntry? = entries.getOrNull(id - 1)

    fun contains(id: Int): Boolean = id == ROOT_ID || entry(id) != null

    /** All properties of [id] (`null` if unknown), restricted to [propertyNames] unless that is empty. */
    fun properties(id: Int, propertyNames: Collection<String> = emptyList()): Map<String, Any>? {
        val all = when {
            id == ROOT_ID -> mapOf("children-display" to "submenu")
            else -> when (val entry = entry(id) ?: return null) {
                is TacitTrayMenuEntry.Item -> mapOf(
                    "label" to escapeDbusMenuLabel(entry.label),
                    "enabled" to true,
                    "visible" to true,
                )

                TacitTrayMenuEntry.Separator -> mapOf("type" to "separator", "visible" to true)
            }
        }
        return if (propertyNames.isEmpty()) all else all.filterKeys { it in propertyNames }
    }

    /**
     * The `GetLayout` tree below [parentId], [recursionDepth] levels deep (`-1`: all, `0`: only the node itself);
     * `null` if [parentId] is unknown.
     */
    fun layout(parentId: Int, recursionDepth: Int, propertyNames: Collection<String>): TacitDbusMenuNode? {
        val properties = properties(parentId, propertyNames) ?: return null
        val children = if (parentId == ROOT_ID && recursionDepth != 0) {
            entries.indices.map { index ->
                val id = index + 1
                TacitDbusMenuNode(id, properties(id, propertyNames).orEmpty(), emptyList())
            }
        } else {
            emptyList()
        }
        return TacitDbusMenuNode(parentId, properties, children)
    }

    companion object {
        const val ROOT_ID = 0

        /** What a host sees of [entries]; the click handlers do not count, so new lambdas do not bump the revision. */
        fun structure(entries: List<TacitTrayMenuEntry>): List<String?> =
            entries.map { (it as? TacitTrayMenuEntry.Item)?.label }
    }
}

/** dbusmenu treats `_` as the mnemonic marker; a literal underscore is written twice. */
internal fun escapeDbusMenuLabel(label: String): String = label.replace("_", "__")

/** The pixels of [image] as the StatusNotifierItem spec wants them: ARGB32, row by row, in network byte order. */
internal fun argb32NetworkOrder(image: BufferedImage): ByteArray {
    val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    val bytes = ByteArray(pixels.size * 4)
    for ((index, argb) in pixels.withIndex()) {
        bytes[index * 4] = (argb ushr 24).toByte()
        bytes[index * 4 + 1] = (argb ushr 16).toByte()
        bytes[index * 4 + 2] = (argb ushr 8).toByte()
        bytes[index * 4 + 3] = argb.toByte()
    }
    return bytes
}
