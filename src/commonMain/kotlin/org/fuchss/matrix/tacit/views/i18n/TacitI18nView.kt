package org.fuchss.matrix.tacit.views.i18n

import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages.DE
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages.EN
import de.connect2x.trixnity.messenger.i18n.GetSystemLang
import de.connect2x.trixnity.messenger.i18n.Languages
import kotlinx.datetime.TimeZone

open class TacitI18nView(
    lang: Languages,
    messengerSettings: MatrixMessengerSettingsHolder,
    getSystemLang: GetSystemLang,
    timeZone: TimeZone,
) : I18nView(lang, messengerSettings, getSystemLang, timeZone) {

    open fun tacitFooterBuiltByPrefix() = translate {
        EN - "Built by "
        DE - "Erstellt von "
    }

    open fun tacitFooterForkedFromPrefix() = translate {
        EN - " · Forked from "
        DE - " · Fork von "
    }

    open fun tacitFooterPoweredByPrefix() = translate {
        EN - " · Powered by "
        DE - " · Powered by "
    }

    open fun tacitAboutTitle() = translate {
        EN - "About Tacit"
        DE - "Über Tacit"
    }

    open fun tacitAboutDescription() = translate {
        EN - "Tacit is a fork of Tammy Messenger focused on a modern, polished Matrix experience."
        DE - "Tacit ist ein Fork von Tammy Messenger mit Fokus auf ein modernes, ausgereiftes Matrix-Erlebnis."
    }

    open fun tacitProjectRepository() = translate {
        EN - "Project repository"
        DE - "Projekt-Repository"
    }

    open fun tacitUpstreamTammy() = translate {
        EN - "Upstream: Tammy"
        DE - "Upstream: Tammy"
    }

    open fun tacitBaseProjectTrixnity() = translate {
        EN - "Base project: Trixnity Messenger"
        DE - "Basisprojekt: Trixnity Messenger"
    }

    open fun tacitMembersTitle(count: Int) = translate {
        EN - "Members · $count"
        DE - "Mitglieder · $count"
    }

    open fun tacitNoMembersToShow() = translate {
        EN - "No members to show"
        DE - "Keine Mitglieder vorhanden"
    }

    open fun tacitYouSuffix(displayName: String) = translate {
        EN - "$displayName (You)"
        DE - "$displayName (Du)"
    }

    open fun tacitToggleMembersPane() = translate {
        EN - "Toggle members pane"
        DE - "Mitgliederleiste umschalten"
    }

    open fun tacitSelectChannelHint() = translate {
        EN - "Select a channel from the list to continue."
        DE - "Wähle einen Kanal aus der Liste, um fortzufahren."
    }

    open fun tacitDecline() = translate {
        EN - "Decline"
        DE - "Ablehnen"
    }

    open fun tacitAccept() = translate {
        EN - "Accept"
        DE - "Annehmen"
    }

    open fun tacitDmStatChats() = translate {
        EN - "Chats"
        DE - "Chats"
    }

    open fun tacitDmStatOnline() = translate {
        EN - "Online"
        DE - "Online"
    }

    open fun tacitDmStatUnread() = translate {
        EN - "Unread"
        DE - "Ungelesen"
    }

    open fun tacitAllDms() = translate {
        EN - "All DMs"
        DE - "Alle DMs"
    }

    open fun tacitRoomsCount(count: Int) = translate {
        EN - "$count rooms"
        DE - "$count Räume"
    }

    open fun tacitRoomsInGuildCount(count: Int) = translate {
        EN - "$count rooms in this guild"
        DE - "$count Räume in dieser Gilde"
    }

    open fun tacitNoJoinedRoomsInGuildTitle() = translate {
        EN - "No joined rooms in this guild"
        DE - "Keine beigetretenen Räume in dieser Gilde"
    }

    open fun tacitNoJoinedRoomsInGuildDescription() = translate {
        EN - "Browse available rooms to get started."
        DE - "Durchsuche verfügbare Räume, um loszulegen."
    }

    open fun tacitBrowseRoomsAction() = translate {
        EN - "Browse Rooms"
        DE - "Räume durchsuchen"
    }

    open fun tacitNoDirectMessagesYet() = translate {
        EN - "No direct messages yet"
        DE - "Noch keine Direktnachrichten"
    }

    open fun tacitNoDirectMessagesDescription() = translate {
        EN - "Start a conversation or join a room."
        DE - "Starte eine Unterhaltung oder tritt einem Raum bei."
    }

    open fun tacitInvitesSection() = translate {
        EN - "INVITES"
        DE - "EINLADUNGEN"
    }

    open fun tacitSearchFriendsPlaceholder() = translate {
        EN - "Search friends"
        DE - "Freunde suchen"
    }

    open fun tacitSearchRoomsPlaceholder() = translate {
        EN - "Search rooms"
        DE - "Räume suchen"
    }

    open fun tacitNewDmDescription() = translate {
        EN - "New DM"
        DE - "Neue DM"
    }

    open fun tacitNewGroupChatDescription() = translate {
        EN - "New group chat"
        DE - "Neuer Gruppenchat"
    }

    open fun tacitNewChannelDescription() = translate {
        EN - "New channel"
        DE - "Neuer Kanal"
    }

    open fun tacitBrowseRoomsDescription() = translate {
        EN - "Browse rooms"
        DE - "Räume durchsuchen"
    }

    open fun tacitInviteMembersDescription() = translate {
        EN - "Invite members"
        DE - "Mitglieder einladen"
    }

    open fun tacitGuildSettingsDescription() = translate {
        EN - "Guild settings"
        DE - "Gilden-Einstellungen"
    }

    open fun tacitClearSearchDescription() = translate {
        EN - "Clear search"
        DE - "Suche löschen"
    }

    open fun tacitNoAccountForGuildInviteError() = translate {
        EN - "No account available for this guild invite."
        DE - "Kein Konto für diese Gilden-Einladung verfügbar."
    }

    open fun tacitCreateRoomTitle() = translate {
        EN - "Create Room"
        DE - "Raum erstellen"
    }

    open fun tacitCreateRoomInGuild(guildName: String) = translate {
        EN - "Create a new room in $guildName."
        DE - "Erstelle einen neuen Raum in $guildName."
    }

    open fun tacitRoomNameLabel() = translate {
        EN - "Room name"
        DE - "Raumname"
    }

    open fun tacitRoomTopicOptionalLabel() = translate {
        EN - "Room topic (optional)"
        DE - "Raumthema (optional)"
    }

    open fun tacitNoAccountForGuild() = translate {
        EN - "No account available for this guild."
        DE - "Kein Konto für diese Gilde verfügbar."
    }

    open fun tacitCreateGuildTitle() = translate {
        EN - "Create Guild"
        DE - "Gilde erstellen"
    }

    open fun tacitCreateGuildDescription() = translate {
        EN - "Create a Matrix Space and use it like a Tacit guild."
        DE - "Erstelle einen Matrix-Space und nutze ihn wie eine Tacit-Gilde."
    }

    open fun tacitGuildNameLabel() = translate {
        EN - "Guild name"
        DE - "Gilden-Name"
    }

    open fun tacitGuildTopicOptionalLabel() = translate {
        EN - "Guild topic (optional)"
        DE - "Gilden-Thema (optional)"
    }

    open fun tacitCreateGeneralRoom() = translate {
        EN - "Create #general room"
        DE - "#general-Raum erstellen"
    }

    open fun tacitNoAccountSelectOrAdd() = translate {
        EN - "No account available. Select or add an account first."
        DE - "Kein Konto verfügbar. Wähle zuerst ein Konto aus oder füge eins hinzu."
    }

    open fun tacitCreateGroupChatTitle() = translate {
        EN - "Create Group Chat"
        DE - "Gruppenchat erstellen"
    }

    open fun tacitCreateGroupChatDescription() = translate {
        EN - "Create an encrypted group chat outside your guilds."
        DE - "Erstelle einen verschlüsselten Gruppenchat außerhalb deiner Gilden."
    }

    open fun tacitNoActiveAccount() = translate {
        EN - "No active Matrix account available."
        DE - "Kein aktives Matrix-Konto verfügbar."
    }

    open fun tacitCreateInProgress() = translate {
        EN - "Creating..."
        DE - "Erstelle..."
    }

    open fun tacitInviteToGuildTitle() = translate {
        EN - "Invite to Guild"
        DE - "In Gilde einladen"
    }

    open fun tacitInviteUserToGuild(guildName: String) = translate {
        EN - "Invite a Matrix user to join $guildName."
        DE - "Lade einen Matrix-Benutzer ein, $guildName beizutreten."
    }

    open fun tacitMatrixUserIdLabel() = translate {
        EN - "Matrix user ID"
        DE - "Matrix-Benutzer-ID"
    }

    open fun tacitUserIdPlaceholder() = translate {
        EN - "@alice:example.org or display name"
        DE - "@alice:example.org oder Anzeigename"
    }

    open fun tacitSearchingUsers() = translate {
        EN - "Searching users..."
        DE - "Suche Benutzer..."
    }

    open fun tacitSearchFailed() = translate {
        EN - "Search failed."
        DE - "Suche fehlgeschlagen."
    }

    open fun tacitReasonOptionalLabel() = translate {
        EN - "Reason (optional)"
        DE - "Grund (optional)"
    }

    open fun tacitInvitingInProgress() = translate {
        EN - "Inviting..."
        DE - "Lade ein..."
    }

    open fun tacitSendInvite() = translate {
        EN - "Send Invite"
        DE - "Einladung senden"
    }

    open fun tacitStartChat() = translate {
        EN - "Start Chat"
        DE - "Chat starten"
    }

    open fun tacitStartDirectMessage() = translate {
        EN - "Start Direct Message"
        DE - "Direktnachricht starten"
    }

    open fun tacitOpenDirectMessageWith(name: String) = translate {
        EN - "Open a direct message with $name?"
        DE - "Direktnachricht mit $name öffnen?"
    }

    open fun tacitSearchUserOrEnterMatrixId() = translate {
        EN - "Search for a user or enter a Matrix user ID."
        DE - "Suche nach einem Benutzer oder gib eine Matrix-Benutzer-ID ein."
    }

    open fun tacitStartingInProgress() = translate {
        EN - "Starting..."
        DE - "Starte..."
    }

    open fun tacitBrowseRoomsTitle() = translate {
        EN - "Browse Rooms"
        DE - "Räume durchsuchen"
    }

    open fun tacitDiscoverAndJoinRooms() = translate {
        EN - "Discover and join rooms"
        DE - "Räume entdecken und beitreten"
    }

    open fun tacitNoDiscoverableRooms() = translate {
        EN - "No discoverable rooms in this guild yet."
        DE - "In dieser Gilde gibt es noch keine auffindbaren Räume."
    }

    open fun tacitJoined() = translate {
        EN - "JOINED"
        DE - "BEIGETRETEN"
    }

    open fun tacitJoiningInProgress() = translate {
        EN - "Joining..."
        DE - "Trete bei..."
    }

    open fun tacitCreateGuildDescriptionIcon() = translate {
        EN - "Create guild"
        DE - "Gilde erstellen"
    }

    open fun tacitAppName() = translate {
        EN - "Tacit"
        DE - "Tacit"
    }

    open fun tacitDirectMessagesDescription() = translate {
        EN - "Direct messages"
        DE - "Direktnachrichten"
    }

    open fun tacitRemoveAvatar() = translate {
        EN - "Remove avatar"
        DE - "Avatar entfernen"
    }

    open fun tacitDirectMessageSettingsTitle() = translate {
        EN - "Direct Message Settings"
        DE - "Direktnachrichten-Einstellungen"
    }

    open fun tacitDirectMessageSettingsInfo() = translate {
        EN - "Direct message rooms use shared account privacy. Room-wide settings are disabled here."
        DE - "Direktnachrichten nutzen gemeinsame Kontodatenschutz-Einstellungen. Raumweite Einstellungen sind hier deaktiviert."
    }

    open fun tacitGuildSettingsTitle() = translate {
        EN - "Guild Settings"
        DE - "Gilden-Einstellungen"
    }

    open fun tacitUserProfileTitle() = translate {
        EN - "User profile"
        DE - "Benutzerprofil"
    }

    open fun tacitUserProfileDescription() = translate {
        EN - "Open a profile to verify or block the user."
        DE - "Profil öffnen, um den Nutzer zu verifizieren oder zu blockieren."
    }

    open fun tacitNoDmContactForProfile() = translate {
        EN - "No DM contact found to open a profile."
        DE - "Kein DM-Kontakt gefunden, um ein Profil zu öffnen."
    }

    open fun tacitOfflineTitle() = translate {
        EN - "Offline"
        DE - "Offline"
    }

    open fun tacitOfflineAllDescription() = translate {
        EN - "No connection to your Matrix server. Messages will be sent once you are back online."
        DE - "Keine Verbindung zum Matrix-Server. Nachrichten werden gesendet, sobald du wieder online bist."
    }

    open fun tacitOfflineSomeDescription(accounts: String) = translate {
        EN - "Some accounts are offline: $accounts"
        DE - "Einige Konten sind offline: $accounts"
    }


    open fun tacitVerified() = translate {
        EN - "Verified"
        DE - "Verifiziert"
    }

    open fun tacitNotVerified() = translate {
        EN - "Not verified"
        DE - "Nicht verifiziert"
    }

    open fun tacitSomeDevicesUnverified() = translate {
        EN - "Some devices unverified"
        DE - "Einige Geräte nicht verifiziert"
    }

    open fun tacitInvalidVerificationState() = translate {
        EN - "Invalid verification state"
        DE - "Ungültiger Verifizierungsstatus"
    }

    open fun tacitBlocked() = translate {
        EN - "Blocked"
        DE - "Blockiert"
    }

    open fun tacitColorHue() = translate {
        EN - "Hue"
        DE - "Farbton"
    }

    open fun tacitColorSaturation() = translate {
        EN - "Saturation"
        DE - "Sättigung"
    }

    open fun tacitColorBrightness() = translate {
        EN - "Brightness"
        DE - "Helligkeit"
    }

    open fun tacitLanguageTitle() = translate {
        EN - "Language"
        DE - "Sprache"
    }

    open fun tacitLanguageSystemDefault() = translate {
        EN - "System default"
        DE - "Systemstandard"
    }

    open fun tacitLanguageEnglish() = translate {
        EN - "English"
        DE - "Englisch"
    }

    open fun tacitLanguageGerman() = translate {
        EN - "German"
        DE - "Deutsch"
    }
}
