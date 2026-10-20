package org.fuchss.matrix.tacit.settings

import de.connect2x.trixnity.messenger.MatrixMessengerSettings
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsBase
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolderImpl
import de.connect2x.trixnity.messenger.ThemeMode
import de.connect2x.trixnity.messenger.settings.SettingsStorage
import de.connect2x.trixnity.messenger.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

class TacitDarkThemeModeTest {
    private fun holder() = MatrixMessengerSettingsHolderImpl(
        storage = object : SettingsStorage {
            override suspend fun write(settings: String) = Unit
            override suspend fun read(): String? = null
        },
        settings = MutableStateFlow(MatrixMessengerSettings(emptyMap())),
    )

    @Test
    fun `pins the system-following default to dark and keeps it there`() = runTest {
        val settings = holder()
        assertEquals(ThemeMode.DEFAULT, settings.value.base.themeMode)
        val worker = launch { TacitDarkThemeMode(settings).doWork() }
        withTimeout(5_000) { settings.first { it.base.themeMode == ThemeMode.DARK } }

        settings.update<MatrixMessengerSettingsBase> { it.copy(themeMode = ThemeMode.LIGHT) }
        withTimeout(5_000) { settings.first { it.base.themeMode == ThemeMode.DARK } }
        worker.cancel()
    }
}
