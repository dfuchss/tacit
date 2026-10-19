package org.fuchss.matrix.tacit.viewmodel.util

import de.connect2x.trixnity.messenger.MatrixMessengerSettings
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolderImpl
import de.connect2x.trixnity.messenger.i18n.DefaultLanguages
import de.connect2x.trixnity.messenger.i18n.GetSystemLang
import de.connect2x.trixnity.messenger.settings.SettingsStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n

/** A [TacitI18n] in [systemLang] with in-memory settings. */
internal fun testTacitI18n(systemLang: String = "en") = TacitI18n(
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
