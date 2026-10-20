package org.fuchss.matrix.tacit

import androidx.compose.runtime.*
import androidx.compose.ui.window.application
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.compose.view.Client
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.DragAndDrop
import de.connect2x.trixnity.messenger.compose.view.profiles.Profiles
import de.connect2x.trixnity.messenger.compose.view.profiles.ShowProfileCreation
import de.connect2x.trixnity.messenger.compose.view.profiles.WithProfileSelection
import de.connect2x.trixnity.messenger.compose.view.theme.IsFocusHighlighting
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessengerConfiguration
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.util.defaultDragAndDropHandler
import de.connect2x.trixnity.messenger.util.RootPath
import de.connect2x.trixnity.messenger.util.defaultUriHandler
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import org.fuchss.matrix.tacit.settings.TacitWindowCloseBehavior
import org.fuchss.matrix.tacit.settings.readTacitWindowCloseBehavior
import org.fuchss.matrix.tacit.update.TacitOs
import org.fuchss.matrix.tacit.update.TacitUpdateController
import org.fuchss.matrix.tacit.update.TacitUpdater
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.koin.dsl.module
import java.awt.Toolkit
import java.awt.dnd.DropTarget
import sun.misc.Unsafe

private val desktopLog: Logger = Logger("org.fuchss.matrix.tacit.TacitDesktopApp")

/**
 * Runs the desktop app until it exits and shuts it down. Returns whether Tacit was launched again while it was shutting
 * down (Windows and Linux, see [runThenShutDown]); `Main` then starts it again once this process has exited.
 */
fun startTacitMultiMessenger(
    args: Array<String>,
    updater: TacitUpdater,
    activeMessengerOnce: (MatrixMessenger, RootViewModel) -> Unit = { _, _ -> },
    configuration: MatrixMultiMessengerConfiguration.() -> Unit,
): Boolean = runBlocking(Dispatchers.Default) {
    desktopLog.info { "Starting Tacit desktop client" }
    desktopLog.info { "command line args: ${args.joinToString { it }}" }
    desktopLog.info { "JVM version: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})" }

    val lifecycle = LifecycleRegistry()
    val updateModule = { module { if (updater.isEnabled) single<TacitUpdateController> { updater } } }
    val matrixMultiMessenger = MatrixMultiMessenger.create {
        configuration()
        modulesFactories += updateModule
        messengerConfiguration { modulesFactories += updateModule }
    }
    val config = matrixMultiMessenger.di.get<MatrixMultiMessengerConfiguration>()
    val appDataDir = matrixMultiMessenger.di.get<RootPath>().path.toNioPath()
    val launchedAgain = runThenShutDown(
        app = { windowGone ->
            matrixMultiMessenger.defaultUriHandler.start(singleInstanceArgs(args, config.appUri, TacitOs.current, appDataDir))
            updater.start()
            tacitMessengerApp(matrixMultiMessenger, lifecycle, updater, activeMessengerOnce, windowGone)
        },
        shutDown = { shutDownGracefully(matrixMultiMessenger) },
        uris = matrixMultiMessenger.defaultUriHandler,
    )
    launchedAgain && usesSingleInstanceLock(TacitOs.current)
}

/**
 * Workaround for https://youtrack.jetbrains.com/issue/CMP-3308 adjusted to use an unsafe callback.
 * (Ported from trixnity-messenger's messengerApp.kt.)
 *
 * Since the module system security makes it impossible to use reflection for this on our minimum target JDK, we need to
 * use sun.misc.Unsafe to try to override the field value, without tripping up reflection security checks. What a
 * beautiful mess.
 */
private fun updateAppClassName(name: String) {
    try {
        val toolkit = Toolkit.getDefaultToolkit()
        if (toolkit::class.java.simpleName != "XToolkit") return
        desktopLog.debug { "AWT Toolkit implementation is ${toolkit::class.java.name}" }
        val classNameField = toolkit.javaClass.getDeclaredField("awtAppClassName")
        desktopLog.debug { "Got field reference $classNameField" }

        try {
            classNameField.isAccessible = true
            classNameField.set(null, name)
        } catch (_: Throwable) {
            val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
            theUnsafe.isAccessible = true
            val unsafe = theUnsafe.get(null) as? Unsafe ?: return
            desktopLog.debug { "Obtained sun.misc.Unsafe instance $unsafe" }

            val fieldBase = unsafe.staticFieldBase(classNameField)
            val fieldOffset = unsafe.staticFieldOffset(classNameField)
            unsafe.putObjectVolatile(fieldBase, fieldOffset, name)
        }

        desktopLog.debug { "Successfully swapped AWT app class to $name" }
    } catch (_: Throwable) {
        // SILENCE
    }
}

private fun tacitMessengerApp(
    matrixMultiMessenger: MatrixMultiMessenger,
    lifecycle: LifecycleRegistry,
    updater: TacitUpdater,
    activeMessengerOnce: (MatrixMessenger, RootViewModel) -> Unit,
    windowGone: () -> Unit,
) {
    val appName = matrixMultiMessenger.di.get<MatrixMultiMessengerConfiguration>().appName
    updateAppClassName(appName)

    application(exitProcessOnExit = false) {
        val shell = rememberTacitDesktopShellState(appName, updater)
        // The profile screen has no settings of its own, so it keeps the last behaviour an active messenger chose.
        var lastCloseBehavior by remember { mutableStateOf(TacitWindowCloseBehavior.BACKGROUND) }
        val globalI18n = remember { matrixMultiMessenger.di.get<TacitI18nView>() }
        LifecycleController(lifecycle, shell.windowState)

        // "Restart to update" must end the app completely, so it bypasses close-to-tray and removes the tray.
        LaunchedEffect(updater) {
            updater.exitRequests.collect { exitApplication() }
        }

        DesktopReopenEffect(shell.activation)
        // From here on, the collector below is gone; a URI is a launch during the shutdown (see runThenShutDown).
        DisposableEffect(Unit) { onDispose(windowGone) }

        // A URI, also the activation URI of a bare relaunch (see singleInstanceArgs), shows the window; on the
        // profile screen as well as with an active messenger.
        LaunchedEffect(matrixMultiMessenger) {
            matrixMultiMessenger.defaultUriHandler.collect {
                shell.activation.requestOpen()
            }
        }

        WithProfileSelection(
            matrixMultiMessenger = matrixMultiMessenger,
            componentContext = DefaultComponentContext(lifecycle),
            activeMessengerOnce = activeMessengerOnce,
            activeMessenger = { matrixMessenger, rootViewModel ->
                val unreadMessages by matrixMessenger.notificationCount.collectAsState()
                val messengerSettings = remember(matrixMessenger) {
                    matrixMessenger.di.get<MatrixMessengerSettingsHolder>()
                }
                val closeBehavior by remember(messengerSettings) {
                    messengerSettings.map { readTacitWindowCloseBehavior(it) }
                }.collectAsState(readTacitWindowCloseBehavior(messengerSettings.value))
                val i18n = remember(matrixMessenger) { matrixMessenger.di.get<TacitI18nView>() }
                val settings by messengerSettings.collectAsState()
                LaunchedEffect(closeBehavior) { lastCloseBehavior = closeBehavior }

                TacitDesktopShell(
                    state = shell,
                    unreadMessages = unreadMessages,
                    i18n = i18n,
                    closeBehavior = closeBehavior,
                    showUpdateCard = true,
                    locals = arrayOf(
                        DI provides matrixMessenger.di,
                        IsFocusHighlighting provides settings.base.isFocusHighlighting,
                    ),
                ) {
                    DesktopDragAndDropEffect(window, matrixMessenger)
                    Client(rootViewModel)
                }
            },
            nonActiveMessenger = {
                val showProfileCreation = remember { mutableStateOf(false) }

                TacitDesktopShell(
                    state = shell,
                    unreadMessages = 0,
                    i18n = globalI18n,
                    closeBehavior = lastCloseBehavior,
                    showUpdateCard = false,
                    locals = arrayOf(
                        DI provides matrixMultiMessenger.di,
                        ShowProfileCreation provides showProfileCreation,
                        IsFocusHighlighting provides false,
                    ),
                ) {
                    Profiles()
                }
            },
        )
    }
}

/** Routes files dropped onto [window] to [matrixMessenger]'s drag-and-drop handler. */
@Composable
private fun DesktopDragAndDropEffect(window: androidx.compose.ui.awt.ComposeWindow, matrixMessenger: MatrixMessenger) {
    DisposableEffect(window, matrixMessenger) {
        val dropTarget = DropTarget()
        val listener = DragAndDrop(
            matrixMessenger.defaultDragAndDropHandler,
            matrixMessenger.di.get<FileSystem>(),
        )
        dropTarget.addDropTargetListener(listener)
        window.rootPane.dropTarget = dropTarget
        onDispose {
            dropTarget.removeDropTargetListener(listener)
            if (window.rootPane.dropTarget === dropTarget) {
                window.rootPane.dropTarget = null
            }
        }
    }
}
