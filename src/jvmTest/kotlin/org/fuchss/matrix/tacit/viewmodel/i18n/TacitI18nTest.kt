package org.fuchss.matrix.tacit.viewmodel.i18n

import de.connect2x.trixnity.messenger.MatrixMessengerSettings
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolderImpl
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages
import de.connect2x.trixnity.messenger.i18n.GetSystemLang
import de.connect2x.trixnity.messenger.settings.SettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitSlashCommandId
import org.fuchss.matrix.tacit.viewmodel.util.RoomOperationError
import org.fuchss.matrix.tacit.viewmodel.util.RoomOperationField
import org.fuchss.matrix.tacit.viewmodel.util.RoomOperationStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TacitI18nTest {
    private fun i18n(systemLang: String) = TacitI18n(
        lang = DefaultLanguages,
        messengerSettings = MatrixMessengerSettingsHolderImpl(
            storage = object : SettingsStorage {
                override suspend fun write(settings: String) = Unit
                override suspend fun read(): String? = null
            },
            settings = MutableStateFlow(MatrixMessengerSettings(emptyMap())),
        ),
        getSystemLang = GetSystemLang { systemLang },
        timeZone = TimeZone.UTC,
    )

    @Test
    fun resolvesActionErrorsInEnglishAndGerman() {
        assertEquals("Could not join room: boom", i18n("en").tacitJoinRoomFailed("boom"))
        assertEquals("Raum konnte nicht betreten werden: boom", i18n("de").tacitJoinRoomFailed("boom"))
        assertEquals("Unbekannter Fehler", i18n("de-DE").tacitUnknownError())
    }

    @Test
    fun mapsRoomOperationErrorsToLocalisedMessages() {
        val en = i18n("en")
        val de = i18n("de")
        assertEquals("Guild name is required", en.tacitErrorMessage(RoomOperationError.MissingField(RoomOperationField.GUILD_NAME)))
        assertEquals("Der Raumname ist erforderlich", de.tacitErrorMessage(RoomOperationError.MissingField(RoomOperationField.ROOM_NAME)))
        assertEquals(
            "Enter a valid Matrix user ID like @alice:example.org",
            en.tacitErrorMessage(RoomOperationError.InvalidUserId()),
        )
        assertEquals(
            "Gib eine gültige Matrix-Nutzer-ID wie @alice:example.org ein",
            de.tacitErrorMessage(RoomOperationError.InvalidUserId()),
        )
        val timeout = RoomOperationError.TimedOut(RoomOperationStep.GUILD_CREATION, RuntimeException())
        assertEquals("Guild creation timed out. Please try again.", en.tacitErrorMessage(timeout))
        assertEquals("Zeitüberschreitung bei: Erstellen der Gilde. Bitte versuche es erneut.", de.tacitErrorMessage(timeout))
        assertEquals("boom", en.tacitErrorMessage(IllegalStateException("boom")))
        assertEquals("Unbekannter Fehler", de.tacitErrorMessage(IllegalStateException()))
    }

    @Test
    fun resolvesEveryRoomOperationStringInBothLanguages() {
        val en = i18n("en")
        val de = i18n("de")
        RoomOperationField.entries.forEach { field ->
            assertEquals(false, en.tacitRequiredField(field).isBlank(), "EN $field")
            assertEquals(false, de.tacitRequiredField(field).isBlank(), "DE $field")
        }
        RoomOperationStep.entries.forEach { step ->
            assertEquals(false, en.tacitOperationStepName(step).isBlank(), "EN $step")
            assertEquals(false, de.tacitOperationStepName(step).isBlank(), "DE $step")
        }
    }

    @Test
    fun resolvesEverySlashCommandDescription() {
        val en = i18n("en")
        val de = i18n("de")
        TacitSlashCommandId.entries.forEach { id ->
            assertEquals(false, en.tacitSlashCommandDescription(id).isBlank(), "EN $id")
            assertEquals(false, de.tacitSlashCommandDescription(id).isBlank(), "DE $id")
        }
        assertEquals("Send an emote message.", en.tacitSlashCommandDescription(TacitSlashCommandId.ME))
        assertEquals("Sendet eine Emote-Nachricht.", de.tacitSlashCommandDescription(TacitSlashCommandId.ME))
    }

    @Test
    fun typingIndicatorUsesUpstreamStrings() {
        val en = i18n("en")
        assertNull(en.tacitTypingIndicator(emptyList()))
        assertEquals("Alice is typing...", en.tacitTypingIndicator(listOf("Alice")))
        assertEquals("Alice and Bob are typing...", en.tacitTypingIndicator(listOf("Alice", "Bob")))
        assertEquals("Alice, Bob and Cy are typing...", en.tacitTypingIndicator(listOf("Alice", "Bob", "Cy")))
        assertEquals("Alice, Bob and others are typing...", en.tacitTypingIndicator(listOf("Alice", "Bob", "Cy", "Di", "Ed")))
        assertEquals("Alice schreibt...", i18n("de").tacitTypingIndicator(listOf("Alice")))
    }
}
