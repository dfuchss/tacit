package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.messenger.MatrixMessengerSettings
import de.connect2x.trixnity.messenger.settings.MutableSettings
import kotlinx.serialization.json.*

private const val TACIT_KEY = "tacit"
private const val ROOM_LIST_KEY = "roomList"
private const val GUILD_ORDER_KEY = "guildOrder"

internal fun readTacitGuildOrder(settingsMap: Map<String, JsonElement>): List<String> {
    val tacit = settingsMap[TACIT_KEY] as? JsonObject ?: return emptyList()
    val roomList = tacit[ROOM_LIST_KEY] as? JsonObject ?: return emptyList()
    val guildOrder = roomList[GUILD_ORDER_KEY] as? JsonArray ?: return emptyList()
    return guildOrder.mapNotNull { it.jsonPrimitive.contentOrNull }
}

internal fun MutableSettings<MatrixMessengerSettings>.writeTacitGuildOrder(order: List<String>) {
    val currentTacit = this[TACIT_KEY] as? JsonObject
    val currentRoomList = currentTacit?.get(ROOM_LIST_KEY) as? JsonObject
    val newRoomList = JsonObject(
        (currentRoomList?.toMap().orEmpty()) + (GUILD_ORDER_KEY to JsonArray(order.map(::JsonPrimitive)))
    )
    val newTacit = JsonObject((currentTacit?.toMap().orEmpty()) + (ROOM_LIST_KEY to newRoomList))
    this[TACIT_KEY] = newTacit
}
