package org.fuchss.matrix.tacit.settings

import de.connect2x.trixnity.messenger.MatrixMessengerSettingsBase
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.ThemeMode
import de.connect2x.trixnity.messenger.Worker
import de.connect2x.trixnity.messenger.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Tacit only has a dark UI and offers no theme mode setting (TacitAppearanceSettingsView replaces upstream's
 * appearance screen and its picker). Upstream's `ThemeSettings.isDarkMode()` follows the system appearance for
 * the default mode, though, and some upstream views branch on it directly (the context menu hairline, the
 * cross-signing wizard's image tint). Pinning the stored mode to [ThemeMode.DARK] makes that true everywhere.
 *
 * Runs as a [Worker], i.e. right after upstream initialised the settings, and keeps the mode pinned if anything
 * (an old settings file, another client of the same file) writes a different one.
 */
internal class TacitDarkThemeMode(private val settings: MatrixMessengerSettingsHolder) : Worker {
    override suspend fun doWork() {
        settings.map { it.base.themeMode }
            .distinctUntilChanged()
            .filter { it != ThemeMode.DARK }
            .collect { settings.update<MatrixMessengerSettingsBase> { it.copy(themeMode = ThemeMode.DARK) } }
    }
}
