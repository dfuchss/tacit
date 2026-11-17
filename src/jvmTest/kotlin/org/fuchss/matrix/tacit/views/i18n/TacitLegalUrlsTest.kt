package org.fuchss.matrix.tacit.views.i18n

import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolderImpl
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages
import de.connect2x.trixnity.messenger.i18n.GetSystemLang
import de.connect2x.trixnity.messenger.settings.SettingsStorage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** Upstream defaults the imprint and privacy policy to connect2x's website; Tacit must never point there. */
class TacitLegalUrlsTest {
    private fun i18nView(systemLanguage: String): TacitI18nView {
        val storage = object : SettingsStorage {
            override suspend fun read(): String? = null
            override suspend fun write(settings: String) {}
        }
        val settings = MatrixMessengerSettingsHolderImpl(storage)
        runBlocking { settings.init() }
        return TacitI18nView(DefaultLanguages, settings, GetSystemLang { systemLanguage }, TimeZone.UTC)
    }

    @Test
    fun `imprint and privacy policy point to Tacit in every language`() {
        listOf("en", "de").forEach { language ->
            val i18n = i18nView(language)
            assertEquals("https://gitlab.com/dfuchss/tacit", i18n.imprintUrl(), "imprint ($language)")
            assertEquals("https://gitlab.com/dfuchss/tacit", i18n.privacyPolicyUrl(), "privacy ($language)")
        }
    }
}
