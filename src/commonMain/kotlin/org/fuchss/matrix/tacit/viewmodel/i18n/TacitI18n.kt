package org.fuchss.matrix.tacit.viewmodel.i18n

import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages.DE
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages.EN
import de.connect2x.trixnity.messenger.i18n.GetSystemLang
import de.connect2x.trixnity.messenger.i18n.I18n
import de.connect2x.trixnity.messenger.i18n.Languages
import kotlinx.datetime.TimeZone
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitSlashCommandId

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
