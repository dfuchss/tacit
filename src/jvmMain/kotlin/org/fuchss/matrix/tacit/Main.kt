package org.fuchss.matrix.tacit

import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.backend.DefaultBackend
import de.connect2x.lognity.config.CoreConfigExtension
import de.connect2x.lognity.config.SerializableConfig
import de.connect2x.lognity.config.extension.ConfigExtension
import de.connect2x.lognity.config.setDefaultConfig
import de.connect2x.trixnity.messenger.compose.view.startMultiMessenger
import de.connect2x.trixnity.messenger.util.getAppPath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.io.asSource
import kotlinx.io.buffered

@OptIn(ExperimentalCoroutinesApi::class)
object Main {
    private fun configureLogging() {
        Backend.set(DefaultBackend)
        SerializableConfig uses CoreConfigExtension
        SerializableConfig uses ConfigExtension {
            registerProvider("MESSENGER_DIR") {
                if (System.getenv("TAMMY_ROOT_PATH") == null && BuildConfig.flavor == Flavor.DEV) {
                    "./app-data"
                } else getAppPath(BuildConfig.appId).toString()
            }
        }
        checkNotNull(this::class.java.getResourceAsStream("/lognity.json")).use { stream ->
            Backend.setDefaultConfig(stream.asSource().buffered())
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        configureLogging()
        startMultiMessenger(args) {
            tammyConfiguration()
            appIcon = "status_icon.png"
        }
    }
}
