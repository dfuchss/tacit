package org.fuchss.matrix.tacit.readmeshots

import com.arkivanov.decompose.DecomposeSettings
import com.arkivanov.decompose.ExperimentalDecomposeApi
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import org.fuchss.matrix.tacit.settings.TacitWindowCloseBehavior
import org.fuchss.matrix.tacit.settings.writeTacitWindowCloseBehavior
import org.fuchss.matrix.tacit.startTacitMultiMessenger
import org.fuchss.matrix.tacit.update.TacitUpdater
import java.nio.file.Files
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Starts the normal, interactive Tacit desktop app (window, tray, theme: `startTacitMultiMessenger` as in `Main.kt`),
 * already logged in as the demo user `tacit` against a throwaway tuwunel homeserver seeded with the README demo data.
 *
 * Everything is isolated: app data lives in a temp dir (never `./app-data` or the real application support
 * directory), the OS keychain is bypassed and the updater stays off. The container and the temp dir are removed when
 * the window is closed or the process exits. Run it with `./gradlew runDemo` (requires Docker).
 */
@OptIn(ExperimentalDecomposeApi::class)
fun main() {
    Locale.setDefault(Locale.ENGLISH)
    val dataDir = Files.createTempDirectory("tacit-demo")
    val container = tuwunelDocker()
    var cleanedUp = false
    fun cleanUp() {
        synchronized(container) {
            if (cleanedUp) return
            cleanedUp = true
        }
        runCatching { container.stop() }
        runCatching { dataDir.toFile().deleteRecursively() }
    }
    Runtime.getRuntime().addShutdownHook(Thread(::cleanUp, "tacit-demo-cleanup"))

    container.start()
    val serverUrl = container.clientServerUrl()
    val users = seedDemoUsers(MatrixHttp(serverUrl))
    seedDemoConversations(users)

    println("=".repeat(72))
    println("Tacit demo homeserver: $serverUrl  (server name: $SERVER_NAME)")
    for (user in listOf("tacit", "tacit2", "mira", "ollie", "pip")) println("  user: $user   password: $DEMO_PASSWORD")
    println("The Tacit window below is logged in as 'tacit'; log in with another client as 'tacit2' to chat.")
    println("Temp data dir: $dataDir")
    println("=".repeat(72))

    configureScreenshotLogging(dataDir.toOkioPath())
    // The login is driven from a background coroutine, like in the screenshot generator.
    DecomposeSettings.update { it.copy(mainThreadCheckEnabled = false) }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
        startTacitMultiMessenger(
            args = emptyArray(),
            updater = TacitUpdater.disabled(dataDir), // never checks, and never touches the real app data dir
            activeMessengerOnce = { messenger, root ->
                scope.launch {
                    // Closing the window quits the demo (instead of hiding it to the tray), so cleanup runs.
                    messenger.di.get<MatrixMessengerSettingsHolder>().update { writeTacitWindowCloseBehavior(TacitWindowCloseBehavior.EXIT) }
                    runCatching { root.loginWithPassword(serverUrl, "tacit", DEMO_PASSWORD) }
                        .onSuccess { println("DEMO login as tacit succeeded") }
                        .onFailure { println("DEMO login FAILED: $it"); it.printStackTrace() }
                }
            },
        ) {
            isolatedTacitConfiguration(dataDir.toOkioPath())
        }
    } finally {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        cleanUp()
    }
    exitProcess(0)
}
