package org.fuchss.matrix.tacit.screenshot

import androidx.compose.ui.window.ComposeUIViewController
import com.arkivanov.essenty.lifecycle.Lifecycle
import de.connect2x.lognity.api.logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages
import platform.UIKit.UIViewController

private val log: Logger = Logger("org.fuchss.matrix.tacit.screenshot.ScreenshotMessengerViewControllerKt")

fun ScreenshotMessengerViewController(lifecycle: Lifecycle, showRoom: MutableStateFlow<Boolean>): UIViewController {
    log.info { "Starting iOS client with SCREENSHOTS" }

    val preferredLanguage = NSLocale.preferredLanguages.firstOrNull() as? String

    return ComposeUIViewController(
        configure = { enforceStrictPlistSanityCheck = false }
    ) {
        ScreenshotView(
            when ((preferredLanguage ?: "en-US").substringBefore("-")) {
                "de" -> Locale.GERMAN
                else -> Locale.ENGLISH
            },
            showRoom,
        )
    }
}