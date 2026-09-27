package org.fuchss.matrix.tacit.readmeshots

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** Geometry of the final README image: the captured app content, edge to edge. */
internal object ScreenshotFrame {
    /** Logical size of the rendered app window (dp). */
    const val CONTENT_WIDTH_DP = 1480
    const val CONTENT_HEIGHT_DP = 860
    const val DENSITY = 2f

    /** Physical size of the capture (what the real app renders on a 2x HiDPI display), and of the PNG. */
    const val CONTENT_WIDTH_PX = (CONTENT_WIDTH_DP * DENSITY).toInt()
    const val CONTENT_HEIGHT_PX = (CONTENT_HEIGHT_DP * DENSITY).toInt()

    /**
     * Encodes the capture as-is: opaque, no transparent padding, no rounded corners, no drop shadow.
     * Whoever shows the image frames it — fuchss.org gives every project screenshot the same border and
     * shadow, and a baked-in frame ends up sitting inside that one as a second, mismatched border.
     * Deliberately platform-neutral: no title bar, no traffic lights.
     */
    fun toPng(capture: ImageBitmap): ByteArray {
        require(capture.width == CONTENT_WIDTH_PX && capture.height == CONTENT_HEIGHT_PX) {
            "unexpected capture size ${capture.width}x${capture.height}"
        }
        return Image.makeFromBitmap(capture.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
}
