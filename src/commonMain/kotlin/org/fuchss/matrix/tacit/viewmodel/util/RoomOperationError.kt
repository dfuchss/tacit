package org.fuchss.matrix.tacit.viewmodel.util

/** Required input that was left blank. */
internal enum class RoomOperationField { GUILD_NAME, ROOM_NAME, CATEGORY_NAME, MATRIX_USER_ID }

/** The step of a room operation that can time out. */
internal enum class RoomOperationStep {
    GUILD_CREATION,
    ROOM_CREATION,
    LINK_ROOM_TO_GUILD,
    LINK_GUILD_TO_ROOM,
    CONFIGURE_ROOM_JOIN_RULES,
    CATEGORY_CREATION,
    LINK_CATEGORY_TO_GUILD,
    LINK_GUILD_TO_CATEGORY,
    CONFIGURE_CATEGORY_JOIN_RULES,
    UNLINK_ROOM_FROM_CATEGORY,
    LINK_ROOM_TO_CATEGORY,
    LINK_CATEGORY_TO_ROOM,
    GROUP_CHAT_CREATION,
    DIRECT_MESSAGE_CREATION,
}

/**
 * Typed, locale-independent failures of room operations. Callers render them with
 * `TacitI18n.tacitErrorMessage`; the exception message is only meant for logs.
 */
internal sealed class RoomOperationError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingField(val field: RoomOperationField) : RoomOperationError("$field is required")

    class InvalidUserId : RoomOperationError("Invalid Matrix user ID")

    class TimedOut(val step: RoomOperationStep, cause: Throwable) : RoomOperationError("$step timed out", cause)
}
