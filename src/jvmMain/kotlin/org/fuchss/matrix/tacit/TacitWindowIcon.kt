package org.fuchss.matrix.tacit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Image
import kotlin.math.roundToInt

/**
 * Window icon for the Tacit desktop app.
 *
 * We deliberately do not reuse [de.connect2x.trixnity.messenger.compose.view.MessengerTrayIcon] here. That painter
 * picks one of four pre-rendered logo bitmaps by canvas width (16/32/64/1024 px) and then draws it with
 * `drawImage(bitmap, topLeft = ...)`, which blits at the bitmap's *natural* pixel size without scaling to the draw
 * scope. Compose Desktop renders a window icon on a fixed 192x192 canvas (`androidx.compose.ui.util.setIcon`, plus a
 * density-scaled variant, e.g. 384x384 on a HiDPI screen). Both land in the `>= 120 px` bucket, so the 1024 px logo
 * gets blitted unscaled into a 192/384 px canvas and only its top-left corner survives. On macOS that image is what
 * AppKit uses for the minimized-window tile in the Dock, so minimizing Tacit showed a corner fragment of the logo.
 *
 * This painter instead always scales the logo to whatever canvas it is handed, so it is correct at every size and on
 * every platform (on Windows/Linux the same window icon feeds the title bar and taskbar).
 */
class TacitWindowIcon(
    private val unreadMessages: Int,
    iconSize: Float = defaultIconSize,
) : Painter() {
    override val intrinsicSize = Size(iconSize, iconSize)

    override fun DrawScope.onDraw() {
        val logo = logoBitmap ?: return
        val width = size.width.roundToInt().coerceAtLeast(1)
        val height = size.height.roundToInt().coerceAtLeast(1)

        // dstSize is the whole point: it scales the 1024 px master down to the canvas instead of cropping it.
        drawImage(
            image = logo,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, height),
            filterQuality = FilterQuality.High,
        )

        if (unreadMessages > 0) {
            // A plain proportional dot rather than a rendered count: this icon is drawn at Dock/title-bar sizes where
            // a number would not be legible anyway, and it stays correct at every canvas size.
            val radius = size.minDimension / 8f
            val inset = radius * 1.15f
            drawCircle(
                color = unreadBadgeColor,
                radius = radius,
                center = Offset(size.width - inset, inset),
            )
        }
    }

    companion object {
        /** Matches the canvas Compose Desktop uses for window icons, so `intrinsicSize` is not misleading. */
        const val defaultIconSize = 192f

        private val unreadBadgeColor = Color(0xFFFF0000)

        private val logoBitmap: ImageBitmap? =
            TacitWindowIcon::class
                .java
                .getResourceAsStream("/logo.png")
                ?.readAllBytes()
                ?.let { Image.makeFromEncoded(it) }
                ?.toComposeImageBitmap()
    }
}
