package org.fuchss.matrix.tacit.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import org.fuchss.matrix.tacit.TacitIconImages

/**
 * Icon sizes handed to the panel, picked from [TacitIconImages.sizes]: Plasma uses 22 px by default and larger ones on
 * bigger panels and HiDPI. Pre-sized images, because upstream's `MessengerTrayIcon` blits fixed 16/32/64 px bitmaps
 * unscaled into the top-left corner, so at 22 or 48 px the logo is off-centre.
 */
private val sniIconSizes = 16..128

/**
 * Counterpart of Compose's `Tray` for [tray]: shows the item while in the composition and keeps its icon, menu and
 * activation in sync. The item is hidden again when this leaves the composition (e.g. on exit).
 */
@Composable
internal fun TacitStatusNotifierTrayItem(
    tray: TacitStatusNotifierTray,
    unread: Boolean,
    onActivate: () -> Unit,
    menu: List<TacitTrayMenuEntry>,
) {
    val images = remember(unread) { TacitIconImages.forUnread(unread).filter { it.width in sniIconSizes } }
    // The setters only swap snapshots; show() merely queues the registration, so the host's first property read
    // already finds icon and menu.
    SideEffect {
        tray.setOnActivate(onActivate)
        tray.setMenu(menu)
    }
    DisposableEffect(tray, images) {
        tray.setIcon(images)
        onDispose { }
    }
    DisposableEffect(tray) {
        tray.show()
        onDispose { tray.hide() }
    }
}
