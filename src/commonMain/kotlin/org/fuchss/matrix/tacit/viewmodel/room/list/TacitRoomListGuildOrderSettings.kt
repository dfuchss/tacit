package org.fuchss.matrix.tacit.viewmodel.room.list

import de.connect2x.trixnity.messenger.MatrixMessengerSettings
import de.connect2x.trixnity.messenger.settings.MutableSettings
import kotlinx.serialization.json.*

private const val TACIT_KEY = "tacit"
private const val ROOM_LIST_KEY = "roomList"
private const val GUILD_ORDER_KEY = "guildOrder"
private const val CATEGORY_ORDER_BY_GUILD_KEY = "categoryOrderByGuild"

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

internal fun readTacitCategoryOrderByGuild(settingsMap: Map<String, JsonElement>): Map<String, List<String>> {
    val tacit = settingsMap[TACIT_KEY] as? JsonObject ?: return emptyMap()
    val roomList = tacit[ROOM_LIST_KEY] as? JsonObject ?: return emptyMap()
    val categoryOrderByGuild = roomList[CATEGORY_ORDER_BY_GUILD_KEY] as? JsonObject ?: return emptyMap()
    return categoryOrderByGuild
        .mapValues { (_, value) ->
            (value as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty()
        }
}

internal fun MutableSettings<MatrixMessengerSettings>.writeTacitCategoryOrderForGuild(
    guildKey: String,
    order: List<String>,
) {
    val currentTacit = this[TACIT_KEY] as? JsonObject
    val currentRoomList = currentTacit?.get(ROOM_LIST_KEY) as? JsonObject
    val currentCategoryOrderByGuild = currentRoomList?.get(CATEGORY_ORDER_BY_GUILD_KEY) as? JsonObject
    val newCategoryOrderByGuild = JsonObject(
        (currentCategoryOrderByGuild?.toMap().orEmpty()) +
                (guildKey to JsonArray(order.map(::JsonPrimitive)))
    )
    val newRoomList = JsonObject(
        (currentRoomList?.toMap().orEmpty()) +
                (CATEGORY_ORDER_BY_GUILD_KEY to newCategoryOrderByGuild)
    )
    val newTacit = JsonObject((currentTacit?.toMap().orEmpty()) + (ROOM_LIST_KEY to newRoomList))
    this[TACIT_KEY] = newTacit
}
