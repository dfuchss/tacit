package org.fuchss.matrix.tacit

import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.backend.DefaultBackend
import de.connect2x.lognity.config.CoreConfigExtension
import de.connect2x.lognity.config.SerializableConfig
import de.connect2x.lognity.config.extension.ConfigExtension
import de.connect2x.lognity.config.setDefaultConfig
import de.connect2x.trixnity.messenger.util.getAppPath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.fuchss.matrix.tacit.update.TacitUpdater
import kotlin.system.exitProcess

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
        val updater = TacitUpdater.create()
        startTacitMultiMessenger(args, updater) {
            tammyConfiguration()
        }
        // The app (windows, tray, databases) is shut down here. The helper waits for this process to exit
        // before it swaps the install, so nothing under the install is touched while Tacit still runs.
        if (updater.launchPendingUpdate()) exitProcess(0)
    }
}
