package org.fuchss.matrix.tacit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.*
import de.connect2x.trixnity.messenger.compose.view.EscapeKeyPressed
import de.connect2x.trixnity.messenger.compose.view.MessengerTrayIcon
import de.connect2x.trixnity.messenger.compose.view.Platform
import de.connect2x.trixnity.messenger.compose.view.PlatformType
import de.connect2x.trixnity.messenger.compose.view.theme.MessengerTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.fuchss.matrix.tacit.settings.TacitWindowCloseBehavior
import org.fuchss.matrix.tacit.update.TacitOs
import org.fuchss.matrix.tacit.update.TacitUpdateCard
import org.fuchss.matrix.tacit.update.TacitUpdateState
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
import kotlin.math.roundToInt

/**
 * Whether hiding the window to the tray disposes it (and with it the whole window composition).
 *
 * `true`: memory and composition work are released while Tacit sits in the tray, but reopening rebuilds the UI, so
 * scroll positions, unsent drafts held only in composition state and the open dialog are lost and the first frame
 * after reopening takes longer.
 * `false`: the window is only made invisible (`Window(visible = false)`), so reopening is instant and keeps that state,
 * at the cost of keeping the composition (and the timeline's images) in memory while hidden.
 */
internal const val DISPOSE_WINDOW_WHEN_HIDDEN = true

/** Window size Tacit asks for on start, before it is clamped to the screen. */
internal val preferredTacitWindowSize = DpSize(1600.dp, 1000.dp)

/**
 * Initial window size for a usable screen area of [screenWidth] x [screenHeight] AWT user-space units (bounds minus taskbar/Dock/menu bar insets).
 *
 * Compose Desktop maps 1 dp to 1 AWT user-space unit (it uses the screen's scale as density), so the screen size
 * must come from [java.awt.GraphicsConfiguration.getBounds], not from `DisplayMode`, which reports device pixels on
 * scaled Windows/Linux screens.
 */
internal fun initialTacitWindowSize(screenWidth: Int, screenHeight: Int): DpSize = DpSize(
    width = min(preferredTacitWindowSize.width, screenWidth.dp),
    height = min(preferredTacitWindowSize.height, screenHeight.dp),
)

internal enum class TacitWindowCloseAction { HIDE_TO_TRAY, EXIT }

/** Closing the window only hides it to the tray if there is a tray to restore it from. */
internal fun tacitWindowCloseAction(
    traySupported: Boolean,
    closeBehavior: TacitWindowCloseBehavior,
): TacitWindowCloseAction =
    if (traySupported && closeBehavior == TacitWindowCloseBehavior.BACKGROUND) {
        TacitWindowCloseAction.HIDE_TO_TRAY
    } else {
        TacitWindowCloseAction.EXIT
    }

/** Unread count drawn into the app/window icon: none if the OS already shows it as a native badge. */
internal fun iconDrawnUnreadCount(unreadMessages: Int, nativeBadge: Boolean): Int =
    if (nativeBadge) 0 else unreadMessages

/** Label for the native app icon badge; `null` removes the badge. */
internal fun taskbarBadgeLabel(unreadMessages: Int): String? =
    if (unreadMessages > 0) unreadMessages.toString() else null

@Stable
internal class WindowActivationController {
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

/** Application-wide shell state shared by the active-messenger and profile-selection branches. */
@Stable
internal class TacitDesktopShellState(
    val appName: String,
    val updater: TacitUpdater,
    val windowState: WindowState,
    val trayState: TrayState,
) {
    val activation = WindowActivationController()
    val escapeKeyPressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** In-memory only: "Later" hides the card for this version until the next start or a newer version. */
    var dismissedUpdateVersion by mutableStateOf<String?>(null)
}

@Composable
internal fun rememberTacitDesktopShellState(appName: String, updater: TacitUpdater): TacitDesktopShellState {
    val initialSize = remember {
        val config = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val bounds = config.bounds
        // Taskbar, Dock and menu bar are screen insets; the window should fit what is left.
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        initialTacitWindowSize(
            screenWidth = bounds.width - insets.left - insets.right,
            screenHeight = bounds.height - insets.top - insets.bottom,
        )
    }
    val windowState = rememberWindowState(size = initialSize)
    val trayState = rememberTrayState()
    return remember(appName, updater, windowState, trayState) {
        TacitDesktopShellState(appName, updater, windowState, trayState)
    }
}

/**
 * Tray, taskbar icon/badge, main window and update card. Used both with an active messenger and on the profile
 * screen; [content] is rendered inside [MessengerTheme] with the desktop composition locals plus [locals].
 */
@Composable
internal fun ApplicationScope.TacitDesktopShell(
    state: TacitDesktopShellState,
    unreadMessages: Int,
    i18n: TacitI18nView,
    closeBehavior: TacitWindowCloseBehavior,
    showUpdateCard: Boolean,
    locals: Array<ProvidedValue<*>>,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    val updateState by state.updater.state.collectAsState()
    val readyVersion = (updateState as? TacitUpdateState.ReadyToRestart)?.version
    val currentCloseBehavior by rememberUpdatedState(closeBehavior)

    TacitDesktopTray(state, unreadMessages, i18n, readyVersion)
    DesktopTaskbarIcon(unreadMessages)

    TacitDesktopWindow(
        state = state,
        unreadMessages = unreadMessages,
        onCloseRequest = {
            when (tacitWindowCloseAction(isTraySupported, currentCloseBehavior)) {
                TacitWindowCloseAction.HIDE_TO_TRAY -> state.activation.hide()
                TacitWindowCloseAction.EXIT -> exitApplication()
            }
        },
    ) {
        CompositionLocalProvider(
            Platform provides PlatformType.DESKTOP,
            EscapeKeyPressed provides state.escapeKeyPressed,
            *locals,
        ) {
            MessengerTheme {
                if (showUpdateCard) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        TacitDesktopUpdateCard(state, i18n, readyVersion)
                    }
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun BoxScope.TacitDesktopUpdateCard(state: TacitDesktopShellState, i18n: TacitI18nView, readyVersion: String?) {
    val uriHandler = LocalUriHandler.current
    TacitUpdateCard(
        i18n = i18n,
        version = readyVersion,
        dismissed = readyVersion != null && readyVersion == state.dismissedUpdateVersion,
        onRestart = state.updater::restartToUpdate,
        onWhatsNew = { uriHandler.openUri(state.updater.releaseUrl) },
        onLater = { state.dismissedUpdateVersion = readyVersion },
        // Top end below the room header: the bottom end holds the composer's send/attach
        // buttons and the timeline's scroll-to-end button.
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 24.dp),
    )
}

@Composable
internal fun DesktopReopenEffect(
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
    state: TacitDesktopShellState,
    unreadMessages: Int,
    i18n: TacitI18nView,
    readyVersion: String?,
) {
    if (!isTraySupported) return
    val updater = state.updater
    LaunchedEffect(readyVersion) {
        if (readyVersion != null && withContext(Dispatchers.IO) { updater.shouldNotify(readyVersion) }) {
            state.trayState.sendNotification(
                Notification(i18n.tacitUpdateNotificationTitle(), i18n.tacitUpdateReady(readyVersion))
            )
        }
    }
    val onOpen = state.activation::requestOpen
    val icon = remember(unreadMessages) { MessengerTrayIcon(unreadMessages) }
    Tray(
        state = state.trayState,
        icon = icon,
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
    // With a native badge the OS shows the counter, so the image itself stays plain.
    val drawnUnread = iconDrawnUnreadCount(unreadMessages, isTaskbarBadgeSupported)
    LaunchedEffect(drawnUnread) {
        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            taskbar.iconImage =
                // Inset to Apple's icon grid on macOS (full-bleed otherwise) so the Dock icon matches other apps.
                TacitWindowIcon(drawnUnread, iconSize = 1024f, artworkFraction = TacitWindowIcon.platformArtworkFraction).toAwtImage(
                    Density(1f),
                    LayoutDirection.Ltr,
                )
        }
    }
    if (isTaskbarBadgeSupported) {
        LaunchedEffect(unreadMessages) {
            Taskbar.getTaskbar().setIconBadge(taskbarBadgeLabel(unreadMessages))
        }
    }
}

/** Windows and Linux get pre-sized window icons; macOS keeps the Compose painter (Dock and minimised tile). */
private val usesSizedIconImages: Boolean = TacitOs.current != TacitOs.MACOS

@Composable
private fun TacitDesktopWindow(
    state: TacitDesktopShellState,
    unreadMessages: Int,
    onCloseRequest: () -> Unit,
    content: @Composable FrameWindowScope.() -> Unit,
) {
    val visible = state.activation.windowVisible
    if (DISPOSE_WINDOW_WHEN_HIDDEN && !visible) return

    // The OS badge already carries the counter, so the window icon must not add a second indicator.
    val drawnUnread = iconDrawnUnreadCount(unreadMessages, isTaskbarBadgeSupported)
    val icon = remember(drawnUnread) {
        TacitWindowIcon(drawnUnread, artworkFraction = TacitWindowIcon.platformArtworkFraction)
    }
    Window(
        onCloseRequest = onCloseRequest,
        visible = visible,
        // Windows/Linux get a list of pre-sized images below; Compose's single 192 px icon would be shrunk speckled.
        icon = if (usesSizedIconImages) null else icon,
        title = state.appName,
        state = state.windowState,
        onPreviewKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                state.escapeKeyPressed.tryEmit(Unit)
            }
            false
        },
    ) {
        // Without a minimum size the window can be dragged far below the smallest layout that
        // still works: the single pane room list needs minTacitWindowWidth before the room names
        // start disappearing behind the fixed chrome (guild rail, avatars, badges).
        // AWT sizes are in user-space units, which Compose Desktop treats 1:1 as dp.
        LaunchedEffect(window) {
            window.minimumSize = Dimension(
                minTacitWindowWidth.value.roundToInt(),
                minTacitWindowHeight.value.roundToInt(),
            )
        }
        if (usesSizedIconImages) {
            // Compose's icon = null has already reset the window's icons at this point (it runs when the window is
            // created, before effects), and an unchanged null never touches them again.
            LaunchedEffect(window, drawnUnread) {
                val images = withContext(Dispatchers.Default) { TacitIconImages.forUnread(drawnUnread > 0) }
                if (images.isNotEmpty()) window.iconImages = images
            }
        }
        LaunchedEffect(state.activation.activationRequest) {
            val requested = state.activation.activationRequest > 0
            // An explicit open (tray, relaunch, URI) also restores a minimised window.
            if (requested) state.windowState.isMinimized = false
            if (requested && TacitOs.current == TacitOs.WINDOWS) {
                // Windows only flashes the taskbar button when a background process calls toFront(); briefly
                // making the window topmost brings it to the front.
                window.isAlwaysOnTop = true
                window.toFront()
                window.isAlwaysOnTop = false
            }
            window.toFront()
            window.requestFocus()
            window.rootPane.requestFocus()
        }
        content()
    }
}
