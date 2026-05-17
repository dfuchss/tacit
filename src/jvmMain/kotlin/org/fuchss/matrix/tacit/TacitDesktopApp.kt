package org.fuchss.matrix.tacit

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.*
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import de.connect2x.lognity.api.logger.Logger
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.compose.view.*
import de.connect2x.trixnity.messenger.compose.view.profiles.Profiles
import de.connect2x.trixnity.messenger.compose.view.profiles.ShowProfileCreation
import de.connect2x.trixnity.messenger.compose.view.profiles.WithProfileSelection
import de.connect2x.trixnity.messenger.compose.view.theme.IsFocusHighlighting
import de.connect2x.trixnity.messenger.compose.view.theme.MessengerTheme
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessengerConfiguration
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.util.defaultDragAndDropHandler
import de.connect2x.trixnity.messenger.util.defaultUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import org.fuchss.matrix.tacit.settings.TacitWindowCloseBehavior
import org.fuchss.matrix.tacit.settings.readTacitWindowCloseBehavior
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Taskbar
import java.awt.desktop.AppForegroundEvent
import java.awt.desktop.AppForegroundListener
import java.awt.desktop.AppReopenedEvent
import java.awt.desktop.AppReopenedListener
import java.awt.dnd.DropTarget

private val desktopLog: Logger = Logger("org.fuchss.matrix.tacit.TacitDesktopApp")

@Stable
private class WindowActivationController {
    var windowVisible by mutableStateOf(true)
    var activationRequest by mutableIntStateOf(0)

    fun requestOpen() {
        windowVisible = true
        activationRequest += 1
        if (Desktop.isDesktopSupported()) {
            runCatching { Desktop.getDesktop().requestForeground(true) }
        }
    }

    fun hide() {
        windowVisible = false
    }
}

fun startTacitMultiMessenger(
    args: Array<String>,
    configuration: MatrixMultiMessengerConfiguration.() -> Unit,
) = runBlocking(Dispatchers.Default) {
    desktopLog.info { "Starting Tacit desktop client" }
    desktopLog.info { "command line args: ${args.joinToString { it }}" }
    desktopLog.info { "JVM version: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})" }

    val lifecycle = LifecycleRegistry()
    val matrixMultiMessenger = MatrixMultiMessenger.create(configuration = configuration)
    matrixMultiMessenger.defaultUriHandler.start(args)
    tacitMessengerApp(matrixMultiMessenger, lifecycle)

    desktopLog.info { "Shutting down client gracefully.." }
    matrixMultiMessenger.closeSuspending()
}

private fun tacitMessengerApp(
    matrixMultiMessenger: MatrixMultiMessenger,
    lifecycle: LifecycleRegistry,
) {
    application(exitProcessOnExit = false) {
        val windowState = rememberDesktopWindowState()
        val escapeKeyPressed = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
        val activationController = remember { WindowActivationController() }
        var lastCloseBehavior by remember { mutableStateOf(TacitWindowCloseBehavior.BACKGROUND) }
        val trayState = rememberTrayState()
        val appName = remember { matrixMultiMessenger.di.get<MatrixMultiMessengerConfiguration>().appName }
        val globalI18n = remember { matrixMultiMessenger.di.get<TacitI18nView>() }
        LifecycleController(lifecycle, windowState)

        DesktopReopenEffect(activationController)

        WithProfileSelection(
            matrixMultiMessenger = matrixMultiMessenger,
            componentContext = DefaultComponentContext(lifecycle),
            activeMessengerOnce = { _, _ -> },
            activeMessenger = { matrixMessenger, rootViewModel ->
                val unreadMessages by matrixMessenger.notificationCount.collectAsState()
                val messengerSettings = remember(matrixMessenger) {
                    matrixMessenger.di.get<MatrixMessengerSettingsHolder>()
                }
                val closeBehavior by remember(messengerSettings) {
                    messengerSettings.map { readTacitWindowCloseBehavior(it) }
                }.collectAsState(readTacitWindowCloseBehavior(messengerSettings.value))
                val i18n = remember(matrixMessenger) { matrixMessenger.di.get<TacitI18nView>() }
                val isFocusHighlighting by messengerSettings.collectAsState()
                lastCloseBehavior = closeBehavior

                LaunchedEffect(matrixMessenger) {
                    matrixMultiMessenger.defaultUriHandler.collect {
                        activationController.requestOpen()
                    }
                }

                TacitDesktopTray(
                    trayState = trayState,
                    unreadMessages = unreadMessages,
                    i18n = i18n,
                    onOpen = activationController::requestOpen,
                )
                DesktopTaskbarIcon(unreadMessages)

                TacitDesktopWindow(
                    visible = activationController.windowVisible,
                    title = appName,
                    windowState = windowState,
                    unreadMessages = unreadMessages,
                    activationRequest = activationController.activationRequest,
                    onCloseRequest = {
                        if (isTraySupported && closeBehavior == TacitWindowCloseBehavior.BACKGROUND) {
                            activationController.hide()
                        } else {
                            exitApplication()
                        }
                    },
                    onEscapePressed = { escapeKeyPressed.tryEmit(Unit) },
                ) {
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

                    CompositionLocalProvider(
                        Platform provides PlatformType.DESKTOP,
                        DI provides matrixMessenger.di,
                        IsFocusHighlighting provides isFocusHighlighting.base.isFocusHighlighting,
                        EscapeKeyPressed provides escapeKeyPressed,
                    ) {
                        MessengerTheme {
                            Client(rootViewModel)
                        }
                    }
                }
            },
            nonActiveMessenger = {
                val showProfileCreation = remember { mutableStateOf(false) }

                TacitDesktopTray(
                    trayState = trayState,
                    unreadMessages = 0,
                    i18n = globalI18n,
                    onOpen = activationController::requestOpen,
                )
                DesktopTaskbarIcon(0)

                TacitDesktopWindow(
                    visible = activationController.windowVisible,
                    title = appName,
                    windowState = windowState,
                    unreadMessages = 0,
                    activationRequest = activationController.activationRequest,
                    onCloseRequest = {
                        if (isTraySupported && lastCloseBehavior == TacitWindowCloseBehavior.BACKGROUND) {
                            activationController.hide()
                        } else {
                            exitApplication()
                        }
                    },
                    onEscapePressed = { escapeKeyPressed.tryEmit(Unit) },
                ) {
                    CompositionLocalProvider(
                        Platform provides PlatformType.DESKTOP,
                        DI provides matrixMultiMessenger.di,
                        ShowProfileCreation provides showProfileCreation,
                        IsFocusHighlighting provides false,
                        EscapeKeyPressed provides escapeKeyPressed,
                    ) {
                        MessengerTheme {
                            Profiles()
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun rememberDesktopWindowState(): WindowState {
    val gd = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
    val width = gd.displayMode.width
    val height = gd.displayMode.height
    return rememberWindowState(
        width = min(1600.dp, width.dp),
        height = min(1000.dp, height.dp),
    )
}

@Composable
private fun DesktopReopenEffect(
    activationController: WindowActivationController,
) {
    DisposableEffect(activationController) {
        if (!Desktop.isDesktopSupported()) {
            onDispose { }
        } else {
            val desktop = Desktop.getDesktop()
            val reopenListener = object : AppReopenedListener {
                override fun appReopened(e: AppReopenedEvent) {
                    if (!activationController.windowVisible) activationController.requestOpen()
                }
            }
            val foregroundListener = object : AppForegroundListener {
                override fun appRaisedToForeground(e: AppForegroundEvent) {
                    if (!activationController.windowVisible) activationController.requestOpen()
                }

                override fun appMovedToBackground(e: AppForegroundEvent) = Unit
            }
            desktop.addAppEventListener(reopenListener)
            desktop.addAppEventListener(foregroundListener)
            onDispose {
                desktop.removeAppEventListener(reopenListener)
                desktop.removeAppEventListener(foregroundListener)
            }
        }
    }
}

@Composable
private fun ApplicationScope.TacitDesktopTray(
    trayState: TrayState,
    unreadMessages: Int,
    i18n: TacitI18nView,
    onOpen: () -> Unit,
) {
    if (!isTraySupported) return
    Tray(
        state = trayState,
        icon = MessengerTrayIcon(unreadMessages),
        onAction = onOpen,
        menu = {
            Item(i18n.tacitTrayOpen(), onClick = onOpen)
            Separator()
            Item(i18n.tacitTrayExit(), onClick = ::exitApplication)
        },
    )
}

@Composable
private fun DesktopTaskbarIcon(unreadMessages: Int) {
    if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
        Taskbar.getTaskbar().iconImage =
            MessengerTrayIcon(unreadMessages, iconSize = 1024f).toAwtImage(
                Density(1f),
                LayoutDirection.Ltr,
            )
    }
}

@Composable
private fun TacitDesktopWindow(
    visible: Boolean,
    title: String,
    windowState: WindowState,
    unreadMessages: Int,
    activationRequest: Int,
    onCloseRequest: () -> Unit,
    onEscapePressed: () -> Unit,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    if (!visible) return

    Window(
        onCloseRequest = onCloseRequest,
        icon = MessengerTrayIcon(unreadMessages),
        title = title,
        state = windowState,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onEscapePressed()
                false
            } else false
        },
    ) {
        LaunchedEffect(activationRequest) {
            window.toFront()
            window.requestFocus()
            window.rootPane.requestFocus()
        }
        content()
    }
}
