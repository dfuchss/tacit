package org.fuchss.matrix.tacit.readmeshots

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Surface

/** Geometry of the final README image: the captured app content placed on a transparent canvas with a shadow. */
internal object ScreenshotFrame {
    /** Logical size of the rendered app window (dp). */
    const val CONTENT_WIDTH_DP = 1480
    const val CONTENT_HEIGHT_DP = 860
    const val DENSITY = 2f

    /** Physical size of the capture (what the real app renders on a 2x HiDPI display). */
    const val CONTENT_WIDTH_PX = (CONTENT_WIDTH_DP * DENSITY).toInt()
    const val CONTENT_HEIGHT_PX = (CONTENT_HEIGHT_DP * DENSITY).toInt()

    /** Final image size; matches the hand-made screenshots so the README layout does not change. */
    const val IMAGE_WIDTH = 3164
    const val IMAGE_HEIGHT = 1898

    private const val CORNER_RADIUS = 22f
    private const val SHADOW_BLUR_SIGMA = 26f
    private const val SHADOW_OFFSET_Y = 18f
    private const val SHADOW_COLOR = 0x8C000000.toInt()

    private val offsetX = (IMAGE_WIDTH - CONTENT_WIDTH_PX) / 2f
    private val offsetY = ((IMAGE_HEIGHT - CONTENT_HEIGHT_PX) / 2f) - 14f

    /**
     * Places the raw capture on a transparent canvas with rounded corners and a soft drop shadow. Deliberately
     * platform-neutral: no title bar, no traffic lights.
     */
    fun toPng(capture: ImageBitmap): ByteArray {
        require(capture.width == CONTENT_WIDTH_PX && capture.height == CONTENT_HEIGHT_PX) {
            "unexpected capture size ${capture.width}x${capture.height}"
        }
        val image = Image.makeFromBitmap(capture.asSkiaBitmap())
        val surface = Surface.makeRasterN32Premul(IMAGE_WIDTH, IMAGE_HEIGHT)
        val canvas = surface.canvas
        val content = RRect.makeXYWH(offsetX, offsetY, CONTENT_WIDTH_PX.toFloat(), CONTENT_HEIGHT_PX.toFloat(), CORNER_RADIUS)

        val shadow = RRect.makeXYWH(
            offsetX,
            offsetY + SHADOW_OFFSET_Y,
            CONTENT_WIDTH_PX.toFloat(),
            CONTENT_HEIGHT_PX.toFloat(),
            CORNER_RADIUS,
        )
        Paint().use { paint ->
            paint.color = SHADOW_COLOR
            paint.isAntiAlias = true
            paint.maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, SHADOW_BLUR_SIGMA)
            canvas.drawRRect(shadow, paint)
        }

        canvas.save()
        canvas.clipRRect(content, ClipMode.INTERSECT, true)
        canvas.drawImage(image, offsetX, offsetY)
        canvas.restore()

        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
}
