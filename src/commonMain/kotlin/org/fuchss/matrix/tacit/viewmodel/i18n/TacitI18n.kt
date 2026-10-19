package org.fuchss.matrix.tacit.viewmodel.i18n

import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages.DE
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages.EN
import de.connect2x.trixnity.messenger.i18n.GetSystemLang
import de.connect2x.trixnity.messenger.i18n.I18n
import de.connect2x.trixnity.messenger.i18n.Languages
import kotlinx.datetime.TimeZone
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitSlashCommandId
import org.fuchss.matrix.tacit.viewmodel.util.RoomOperationError
import org.fuchss.matrix.tacit.viewmodel.util.RoomOperationField
import org.fuchss.matrix.tacit.viewmodel.util.RoomOperationStep

/**
 * View-model-side Tacit strings. Registered as the app's [I18n] (see `tammyTacitModule`), so upstream view
 * models resolving `get<I18n>()` get this subclass as well. Strings that only views render live in
 * `TacitI18nView` instead.
 */
open class TacitI18n(
    lang: Languages,
    messengerSettings: MatrixMessengerSettingsHolder,
    getSystemLang: GetSystemLang,
    timeZone: TimeZone,
) : I18n(lang, messengerSettings, getSystemLang, timeZone) {

    // ---- typing indicator: composed from upstream strings ----

    /**
     * [names] are display names, already ordered. Mirrors upstream `typingInfo` (which is suspend and works on a
     * `TypingEventContent`, so it does not fit the already resolved member list used here).
     */
    fun tacitTypingIndicator(names: List<String>): String? = when (names.size) {
        0 -> null
        1 -> roomHeaderTypingSingle(names[0])
        in 2..4 -> roomHeaderTypingMultiple(commonAnd(names.dropLast(1).joinToString(), names.last()))
        else -> roomHeaderTypingMultipleMore(names.take(2).joinToString())
    }

    // ---- room list actions ----

    open fun tacitUnknownError() = translate {
        EN - "Unknown error"
        DE - "Unbekannter Fehler"
    }

    open fun tacitNoActiveAccount() = translate {
        EN - "No active Matrix account available."
        DE - "Kein aktives Matrix-Konto verfügbar."
    }

    open fun tacitNoActiveAccountForGuildCreation() = translate {
        EN - "No active Matrix account available for guild creation."
        DE - "Kein aktives Matrix-Konto für das Erstellen einer Gilde verfügbar."
    }

    open fun tacitNoGuildContext() = translate {
        EN - "Could not resolve active guild context."
        DE - "Aktive Gilde konnte nicht ermittelt werden."
    }

    open fun tacitNoAccountForGuild() = translate {
        EN - "No account available for this guild."
        DE - "Für diese Gilde ist kein Konto verfügbar."
    }

    open fun tacitNoAccountForGuildInvite() = translate {
        EN - "No account available for this guild invite."
        DE - "Für diese Gilden-Einladung ist kein Konto verfügbar."
    }

    open fun tacitInvalidUserId(userId: String) = translate {
        EN - "No valid userId provided: '$userId'"
        DE - "Keine gültige Nutzer-ID angegeben: „$userId“"
    }

    open fun tacitCreateGuildFailed(reason: String) = translate {
        EN - "Could not create guild: $reason"
        DE - "Gilde konnte nicht erstellt werden: $reason"
    }

    open fun tacitCreateRoomFailed(reason: String) = translate {
        EN - "Could not create room: $reason"
        DE - "Raum konnte nicht erstellt werden: $reason"
    }

    open fun tacitJoinRoomFailed(reason: String) = translate {
        EN - "Could not join room: $reason"
        DE - "Raum konnte nicht betreten werden: $reason"
    }

    open fun tacitSendInviteFailed(reason: String) = translate {
        EN - "Could not send invite: $reason"
        DE - "Einladung konnte nicht gesendet werden: $reason"
    }

    open fun tacitUserSearchFailed(reason: String) = translate {
        EN - "Search failed: $reason"
        DE - "Suche fehlgeschlagen: $reason"
    }

    open fun tacitStartDirectMessageFailed(reason: String) = translate {
        EN - "Could not start direct message: $reason"
        DE - "Direktnachricht konnte nicht gestartet werden: $reason"
    }

    open fun tacitCreateGroupChatFailed(reason: String) = translate {
        EN - "Could not create group chat: $reason"
        DE - "Gruppenchat konnte nicht erstellt werden: $reason"
    }

    open fun tacitCreateCategoryFailed(reason: String) = translate {
        EN - "Could not create category: $reason"
        DE - "Kategorie konnte nicht erstellt werden: $reason"
    }

    open fun tacitMoveRoomToCategoryFailed(reason: String) = translate {
        EN - "Could not move room to category: $reason"
        DE - "Raum konnte nicht in die Kategorie verschoben werden: $reason"
    }

    open fun tacitAcceptInviteFailed(reason: String) = translate {
        EN - "Could not accept invite: $reason"
        DE - "Einladung konnte nicht angenommen werden: $reason"
    }

    open fun tacitDeclineInviteFailed(reason: String) = translate {
        EN - "Could not decline invite: $reason"
        DE - "Einladung konnte nicht abgelehnt werden: $reason"
    }

    // ---- room operation errors ----

    /** Localised text for any failure: typed [RoomOperationError]s are translated, everything else uses its message. */
    internal fun tacitErrorMessage(error: Throwable): String = when (error) {
        is RoomOperationError.MissingField -> tacitRequiredField(error.field)
        is RoomOperationError.InvalidUserId -> tacitEnterValidMatrixUserId()
        is RoomOperationError.TimedOut -> tacitOperationTimedOut(tacitOperationStepName(error.step))
        else -> error.message ?: tacitUnknownError()
    }

    internal fun tacitRequiredField(field: RoomOperationField) = when (field) {
        RoomOperationField.GUILD_NAME -> translate {
            EN - "Guild name is required"
            DE - "Der Gildenname ist erforderlich"
        }

        RoomOperationField.ROOM_NAME -> translate {
            EN - "Room name is required"
            DE - "Der Raumname ist erforderlich"
        }

        RoomOperationField.CATEGORY_NAME -> translate {
            EN - "Category name is required"
            DE - "Der Kategoriename ist erforderlich"
        }

        RoomOperationField.MATRIX_USER_ID -> translate {
            EN - "Matrix user ID is required"
            DE - "Die Matrix-Nutzer-ID ist erforderlich"
        }
    }

    open fun tacitEnterValidMatrixUserId() = translate {
        EN - "Enter a valid Matrix user ID like @alice:example.org"
        DE - "Gib eine gültige Matrix-Nutzer-ID wie @alice:example.org ein"
    }

    open fun tacitOperationTimedOut(operation: String) = translate {
        EN - "$operation timed out. Please try again."
        DE - "Zeitüberschreitung bei: $operation. Bitte versuche es erneut."
    }

    internal fun tacitOperationStepName(step: RoomOperationStep) = when (step) {
        RoomOperationStep.GUILD_CREATION -> translate {
            EN - "Guild creation"
            DE - "Erstellen der Gilde"
        }

        RoomOperationStep.ROOM_CREATION -> translate {
            EN - "Room creation"
            DE - "Erstellen des Raums"
        }

        RoomOperationStep.LINK_ROOM_TO_GUILD -> translate {
            EN - "Link room to guild"
            DE - "Verknüpfen des Raums mit der Gilde"
        }

        RoomOperationStep.LINK_GUILD_TO_ROOM -> translate {
            EN - "Link guild to room"
            DE - "Verknüpfen der Gilde mit dem Raum"
        }

        RoomOperationStep.CONFIGURE_ROOM_JOIN_RULES -> translate {
            EN - "Configure room join rules"
            DE - "Konfigurieren der Beitrittsregeln des Raums"
        }

        RoomOperationStep.CATEGORY_CREATION -> translate {
            EN - "Category creation"
            DE - "Erstellen der Kategorie"
        }

        RoomOperationStep.LINK_CATEGORY_TO_GUILD -> translate {
            EN - "Link category to guild"
            DE - "Verknüpfen der Kategorie mit der Gilde"
        }

        RoomOperationStep.LINK_GUILD_TO_CATEGORY -> translate {
            EN - "Link guild to category"
            DE - "Verknüpfen der Gilde mit der Kategorie"
        }

        RoomOperationStep.CONFIGURE_CATEGORY_JOIN_RULES -> translate {
            EN - "Configure category join rules"
            DE - "Konfigurieren der Beitrittsregeln der Kategorie"
        }

        RoomOperationStep.UNLINK_ROOM_FROM_CATEGORY -> translate {
            EN - "Unlink room from previous category"
            DE - "Lösen des Raums aus der vorherigen Kategorie"
        }

        RoomOperationStep.LINK_ROOM_TO_CATEGORY -> translate {
            EN - "Link room to category"
            DE - "Verknüpfen des Raums mit der Kategorie"
        }

        RoomOperationStep.LINK_CATEGORY_TO_ROOM -> translate {
            EN - "Link category to room"
            DE - "Verknüpfen der Kategorie mit dem Raum"
        }

        RoomOperationStep.GROUP_CHAT_CREATION -> translate {
            EN - "Group chat creation"
            DE - "Erstellen des Gruppenchats"
        }

        RoomOperationStep.DIRECT_MESSAGE_CREATION -> translate {
            EN - "Direct message creation"
            DE - "Erstellen der Direktnachricht"
        }
    }

    // ---- slash commands ----

    internal fun tacitSlashCommandDescription(id: TacitSlashCommandId) = when (id) {
        TacitSlashCommandId.SPOILER -> translate {
            EN - "Hide text behind a spoiler. Use `reason | message` for a label."
            DE - "Verbirgt Text hinter einem Spoiler. Mit `Grund | Nachricht` fügst du eine Beschriftung hinzu."
        }

        TacitSlashCommandId.ME -> translate {
            EN - "Send an emote message."
            DE - "Sendet eine Emote-Nachricht."
        }

        TacitSlashCommandId.NOTICE -> translate {
            EN - "Send a notice message instead of regular chat text."
            DE - "Sendet eine Hinweis-Nachricht statt normalem Chat-Text."
        }

        TacitSlashCommandId.SHRUG -> translate {
            EN - "Append a shrug to your message."
            DE - "Hängt ein Schulterzucken an deine Nachricht an."
        }

        TacitSlashCommandId.TABLEFLIP -> translate {
            EN - "Append a table flip."
            DE - "Hängt einen Tischflip an."
        }

        TacitSlashCommandId.UNFLIP -> translate {
            EN - "Append a table unflip."
            DE - "Hängt einen zurückgedrehten Tisch an."
        }
    }
}
