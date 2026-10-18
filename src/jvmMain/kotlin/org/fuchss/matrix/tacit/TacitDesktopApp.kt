package org.fuchss.matrix.tacit

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
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
import de.connect2x.trixnity.messenger.MatrixMessenger
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
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import okio.FileSystem
import org.fuchss.matrix.tacit.settings.TacitWindowCloseBehavior
import org.fuchss.matrix.tacit.settings.readTacitWindowCloseBehavior
import org.fuchss.matrix.tacit.update.TacitUpdateController
import org.fuchss.matrix.tacit.update.TacitUpdateState
import org.fuchss.matrix.tacit.update.TacitUpdateCard
import org.fuchss.matrix.tacit.update.TacitUpdater
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.minTacitWindowHeight
import org.fuchss.matrix.tacit.views.minTacitWindowWidth
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.desktop.AppForegroundEvent
import java.awt.desktop.AppForegroundListener
import java.awt.desktop.AppReopenedEvent
import java.awt.desktop.AppReopenedListener
import java.awt.dnd.DropTarget
import kotlin.math.roundToInt
import sun.misc.Unsafe

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
    updater: TacitUpdater,
    activeMessengerOnce: (MatrixMessenger, RootViewModel) -> Unit = { _, _ -> },
    configuration: MatrixMultiMessengerConfiguration.() -> Unit,
) = runBlocking(Dispatchers.Default) {
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
    matrixMultiMessenger.defaultUriHandler.start(args)
    updater.start()
    tacitMessengerApp(matrixMultiMessenger, lifecycle, updater, activeMessengerOnce)

    desktopLog.info { "Shutting down client gracefully.." }
    matrixMultiMessenger.closeSuspending()
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
) {
    updateAppClassName(matrixMultiMessenger.di.get<MatrixMultiMessengerConfiguration>().appName)

    application(exitProcessOnExit = false) {
        val windowState = rememberDesktopWindowState()
        val escapeKeyPressed = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
        val activationController = remember { WindowActivationController() }
        var lastCloseBehavior by remember { mutableStateOf(TacitWindowCloseBehavior.BACKGROUND) }
        val trayState = rememberTrayState()
        val appName = remember { matrixMultiMessenger.di.get<MatrixMultiMessengerConfiguration>().appName }
        val globalI18n = remember { matrixMultiMessenger.di.get<TacitI18nView>() }
        val updateState by updater.state.collectAsState()
        // In-memory only: "Later" hides the card for this version until the next start or a newer version.
        var dismissedUpdateVersion by remember { mutableStateOf<String?>(null) }
        LifecycleController(lifecycle, windowState)

        // "Restart to update" must end the app completely, so it bypasses close-to-tray and removes the tray.
        LaunchedEffect(updater) {
            updater.exitRequests.collect { exitApplication() }
        }

        DesktopReopenEffect(activationController)

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
                    updater = updater,
                    updateState = updateState,
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
                            Box(Modifier.fillMaxSize()) {
                                Client(rootViewModel)
                                val ready = updateState as? TacitUpdateState.ReadyToRestart
                                val uriHandler = LocalUriHandler.current
                                TacitUpdateCard(
                                    i18n = i18n,
                                    version = ready?.version,
                                    dismissed = ready != null && ready.version == dismissedUpdateVersion,
                                    onRestart = updater::restartToUpdate,
                                    onWhatsNew = { uriHandler.openUri(updater.releaseUrl) },
                                    onLater = { dismissedUpdateVersion = ready?.version },
                                    // Top end below the room header: the bottom end holds the composer's send/attach
                                    // buttons and the timeline's scroll-to-end button.
                                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 24.dp),
                                )
                            }
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
                    updater = updater,
                    updateState = updateState,
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
    updater: TacitUpdater,
    updateState: TacitUpdateState,
    onOpen: () -> Unit,
) {
    if (!isTraySupported) return
    val readyVersion = (updateState as? TacitUpdateState.ReadyToRestart)?.version
    LaunchedEffect(readyVersion) {
        if (readyVersion != null && updater.shouldNotify(readyVersion)) {
            trayState.sendNotification(
                Notification(i18n.tacitUpdateNotificationTitle(), i18n.tacitUpdateReady(readyVersion))
            )
        }
    }
    Tray(
        state = trayState,
        icon = MessengerTrayIcon(unreadMessages),
        onAction = onOpen,
        menu = {
            Item(i18n.tacitTrayOpen(), onClick = onOpen)
            if (readyVersion != null) {
                Item(i18n.tacitUpdateRestartToUpdate(), onClick = updater::restartToUpdate)
            }
            Separator()
            Item(i18n.tacitTrayExit(), onClick = ::exitApplication)
        },
    )
}

/** Whether the OS draws the unread counter on the app icon itself (the macOS Dock badge). */
private val isTaskbarBadgeSupported: Boolean =
    Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_BADGE_NUMBER)

@Composable
private fun DesktopTaskbarIcon(unreadMessages: Int) {
    if (!Taskbar.isTaskbarSupported()) return
    val taskbar = Taskbar.getTaskbar()
    if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
        // With a native badge the OS shows the counter, so the image itself stays plain.
        val drawnUnread = if (isTaskbarBadgeSupported) 0 else unreadMessages
        taskbar.iconImage =
            MessengerTrayIcon(drawnUnread, iconSize = 1024f).toAwtImage(
                Density(1f),
                LayoutDirection.Ltr,
            )
    }
    if (isTaskbarBadgeSupported) {
        taskbar.setIconBadge(if (unreadMessages > 0) unreadMessages.toString() else null)
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
        // The OS badge already carries the counter, so the window icon must not add a second indicator.
        icon = TacitWindowIcon(if (isTaskbarBadgeSupported) 0 else unreadMessages),
        title = title,
        state = windowState,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onEscapePressed()
                false
            } else false
        },
    ) {
        // Without a minimum size the window can be dragged far below the smallest layout that
        // still works: the single pane room list needs minTacitWindowWidth before the room names
        // start disappearing behind the fixed chrome (guild rail, avatars, badges).
        LaunchedEffect(window) {
            window.minimumSize = Dimension(
                minTacitWindowWidth.value.roundToInt(),
                minTacitWindowHeight.value.roundToInt(),
            )
        }
        LaunchedEffect(activationRequest) {
            window.toFront()
            window.requestFocus()
            window.rootPane.requestFocus()
        }
        content()
    }
}
