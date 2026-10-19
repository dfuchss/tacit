package org.fuchss.matrix.tacit.viewmodel.i18n

import de.connect2x.trixnity.messenger.MatrixMessengerSettings
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolderImpl
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages
import de.connect2x.trixnity.messenger.i18n.GetSystemLang
import de.connect2x.trixnity.messenger.settings.SettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitSlashCommandId
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
