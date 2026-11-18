package org.fuchss.matrix.tacit.viewmodel.space

/*
 * The `m.space.child` order semantics of Trixnity Messenger v4.11.0, viewmodel/spaces/SpaceContent.kt
 * (`spaceChildComparator`, internal there and tied to its browser items). Replace with an import once Trixnity
 * Messenger makes a reusable variant public.
 */

/** The `order` of an `m.space.child` event if it is valid: at most 50 printable ASCII characters. */
internal fun validSpaceChildOrder(order: String?): String? =
    order?.takeIf { it.length <= 50 && it.all { char -> char in ' '..'~' } }

/**
 * Sorts space children by their valid `m.space.child` [order]: children with an order first, lexicographically by it.
 * Children without one are equal here, so that the caller can break ties (TM uses the event's timestamp and the room
 * id, Tacit the name).
 */
internal fun <T> spaceChildOrderComparator(order: (T) -> String?): Comparator<T> =
    compareBy<T> { order(it) == null }.thenBy { order(it) }
