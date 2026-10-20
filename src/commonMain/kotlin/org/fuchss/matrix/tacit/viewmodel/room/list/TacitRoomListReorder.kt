package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.core.model.RoomId
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import kotlin.math.roundToInt

/*
 * Pure helpers behind reordering guilds and categories and keeping the guild selection in sync with the guild list.
 */

/** Whether moving the item at [fromIndex] to [toIndex] of a list of [size] items changes anything. */
internal fun isMove(fromIndex: Int?, toIndex: Int?, size: Int): Boolean =
    fromIndex != null && toIndex != null && fromIndex in 0..<size && toIndex in 0..<size && fromIndex != toIndex

/** This list with the item at [fromIndex] moved to [toIndex], or null if that is no [isMove]. */
internal fun <T> List<T>.moved(fromIndex: Int, toIndex: Int): List<T>? =
    if (!isMove(fromIndex, toIndex, size)) null
    else toMutableList().apply { add(toIndex, removeAt(fromIndex)) }

/**
 * The guild keys of [guilds] after moving the guild with [guildKey] to [toIndex] (clamped to the list), or null if that
 * guild is gone or nothing moves. Resolving the guild by key keeps a drop correct when the list changed mid-drag.
 */
internal fun guildOrderAfterMove(guilds: List<GuildEntry>, guildKey: String, toIndex: Int): List<String>? {
    val keys = guilds.map { it.key() }
    val fromIndex = keys.indexOf(guildKey)
    if (fromIndex < 0) return null
    return keys.moved(fromIndex, toIndex.coerceIn(0, keys.lastIndex))
}

/** The category room ids of [groups] after moving the category at [fromIndex] to [toIndex], or null if nothing moves. */
internal fun categoryOrderAfterMove(groups: List<TacitChannelGroup<*>>, fromIndex: Int, toIndex: Int): List<RoomId>? =
    groups.map { it.categoryRoomId }.moved(fromIndex, toIndex)

/**
 * The index a guild pill dragged from [fromIndex] by [offsetPx] would drop at, with pills [stepPx] apart, clamped to the
 * [size] guilds of the rail.
 */
internal fun guildDropIndex(fromIndex: Int, offsetPx: Float, stepPx: Float, size: Int): Int =
    (fromIndex + (offsetPx / stepPx).roundToInt()).coerceIn(0, (size - 1).coerceAtLeast(0))

/**
 * The index of the guild pill before which the drop hint is shown while a pill is dragged from [fromIndex] to
 * [dropIndex]; [size] means after the last pill. Null while the drag would not move the pill.
 */
internal fun guildDropHintIndex(fromIndex: Int?, dropIndex: Int?, size: Int): Int? = when {
    !isMove(fromIndex, dropIndex, size) -> null
    dropIndex!! > fromIndex!! -> (dropIndex + 1).coerceAtMost(size)
    else -> dropIndex
}

/**
 * The guild to keep selected after the guild list changed to [guilds]: the refreshed entry of the [selected] guild
 * (same room, same account), or null once it is gone.
 */
internal fun reselectGuild(selected: GuildEntry?, guilds: List<GuildEntry>): GuildEntry? =
    selected?.let { guild -> guilds.firstOrNull { it.roomId == guild.roomId && it.userId == guild.userId } }
