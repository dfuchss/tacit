package org.fuchss.matrix.tacit

import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.api.logger.Logger
import de.connect2x.lognity.api.logger.error
import de.connect2x.lognity.backend.DefaultBackend
import de.connect2x.lognity.config.CoreConfigExtension
import de.connect2x.lognity.config.SerializableConfig
import de.connect2x.lognity.config.extension.ConfigExtension
import de.connect2x.lognity.config.setDefaultConfig
import de.connect2x.trixnity.messenger.util.getAppPath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.fuchss.matrix.tacit.update.TacitOs
import org.fuchss.matrix.tacit.update.TacitUpdater
import java.nio.file.Paths
import kotlin.system.exitProcess

private val mainLog: Logger = Logger("org.fuchss.matrix.tacit.Main")

@OptIn(ExperimentalCoroutinesApi::class)
object Main {
    /** The app data directory, as upstream's `RootPath` resolves it (see `getDevRootPath`). */
    private fun messengerDir(): String =
        if (System.getenv("TAMMY_ROOT_PATH") == null && BuildConfig.flavor == Flavor.DEV) {
            "./app-data"
        } else getAppPath(BuildConfig.appId).toString()

    private fun configureLogging() {
        val messengerDir = messengerDir()
        Backend.set(DefaultBackend)
        SerializableConfig uses CoreConfigExtension
        SerializableConfig uses ConfigExtension {
            registerProvider("MESSENGER_DIR") { messengerDir }
        }
        val choice = loggingConfigChoice(TacitOs.current, Paths.get(messengerDir))
        checkNotNull(this::class.java.getResourceAsStream(choice.resource)).use { stream ->
            Backend.setDefaultConfig(stream.asSource().buffered())
        }
        mainLog.info { "logging config ${choice.resource}: ${choice.reason}" }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        configureLogging()
        val updater = TacitUpdater.create()
        var launchedAgain = false
        val exitCode = try {
            launchedAgain = startTacitMultiMessenger(args, updater) {
                tammyConfiguration()
            }
            0
        } catch (e: Throwable) {
            mainLog.error(e) { "Tacit stopped with an error" }
            1
        }
        // The app (windows, tray, databases) is shut down here. The helper waits for this process to exit
        // before it swaps the install, so nothing under the install is touched while Tacit still runs.
        // It starts Tacit again itself, so a launch during the shutdown needs no relaunch of its own then.
        val updating = updater.launchPendingUpdate()
        if (launchedAgain && !updating) relaunchAfterExit(TacitOs.current, System.getProperty("jpackage.app-path"))
        // Always end the process explicitly: on Windows and Linux upstream's single-instance listener keeps its
        // server socket open on a non-daemon thread, so returning from main would leave a windowless process that
        // holds the port and port.lock (removed by a shutdown hook) and swallows every later launch.
        exitProcess(exitCode)
    }
}
