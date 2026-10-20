package org.fuchss.matrix.tacit

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.fuchss.matrix.tacit.settings.TacitWindowCloseBehavior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TacitDesktopShellTest {
    @Test
    fun initialSizeIsPreferredSizeOnLargeScreens() {
        assertEquals(DpSize(1600.dp, 1000.dp), initialTacitWindowSize(2560, 1440))
    }

    @Test
    fun initialSizeIsClampedPerAxisToScreen() {
        // MacBook Pro 14" default scaling: 1470 x 956 points (2x Retina backing store).
        assertEquals(DpSize(1470.dp, 956.dp), initialTacitWindowSize(1470, 956))
        // 2560 x 1440 px at 150 % is 1707 x 960 user-space units; only the height is clamped.
        assertEquals(DpSize(1600.dp, 960.dp), initialTacitWindowSize(1707, 960))
    }

    @Test
    fun initialSizeFitsUsableAreaWithoutTaskbarOrDock() {
        // MacBook Pro 14": 1470 x 956 bounds minus 33 menu bar and 90 Dock.
        assertEquals(DpSize(1470.dp, 833.dp), initialTacitWindowSize(1470, 956 - 33 - 90))
        // Windows 1920 x 1080 minus a 40 px taskbar.
        assertEquals(DpSize(1600.dp, 1000.dp), initialTacitWindowSize(1920, 1080 - 40))
        // Windows 1366 x 768 minus a 40 px taskbar.
        assertEquals(DpSize(1366.dp, 728.dp), initialTacitWindowSize(1366, 768 - 40))
    }

    @Test
    fun closeHidesToTrayOnlyWithTrayAndBackgroundBehavior() {
        assertEquals(
            TacitWindowCloseAction.HIDE_TO_TRAY,
            tacitWindowCloseAction(traySupported = true, closeBehavior = TacitWindowCloseBehavior.BACKGROUND),
        )
        assertEquals(
            TacitWindowCloseAction.EXIT,
            tacitWindowCloseAction(traySupported = true, closeBehavior = TacitWindowCloseBehavior.EXIT),
        )
        assertEquals(
            TacitWindowCloseAction.EXIT,
            tacitWindowCloseAction(traySupported = false, closeBehavior = TacitWindowCloseBehavior.BACKGROUND),
        )
        assertEquals(
            TacitWindowCloseAction.EXIT,
            tacitWindowCloseAction(traySupported = false, closeBehavior = TacitWindowCloseBehavior.EXIT),
        )
    }

    @Test
    fun iconDrawsUnreadOnlyWithoutNativeBadge() {
        assertEquals(0, iconDrawnUnreadCount(5, nativeBadge = true))
        assertEquals(5, iconDrawnUnreadCount(5, nativeBadge = false))
        assertEquals(0, iconDrawnUnreadCount(0, nativeBadge = false))
    }

    @Test
    fun badgeLabelIsRemovedWhenNothingIsUnread() {
        assertNull(taskbarBadgeLabel(0))
        assertEquals("1", taskbarBadgeLabel(1))
        assertEquals("120", taskbarBadgeLabel(120))
    }
}
