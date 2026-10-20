package org.fuchss.matrix.tacit

import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.imageio.ImageIO

/**
 * Pre-sized window icons for Windows and Linux.
 *
 * Compose renders a window icon into one 192 px image and the JDK then shrinks that in a single bilinear step to the
 * 16-64 px Windows asks for, which skips most source pixels and looks speckled. Giving the window an image of exactly
 * the requested size makes the JDK use it unscaled, so each size is prepared here with an area-averaging downscale.
 */
internal object TacitIconImages {
    /** Windows small icons (16/20/24/32 at 100-200 %) and big icons (32/40/48/64), plus larger ones for Linux shells. */
    val sizes: List<Int> = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    private val master: BufferedImage? by lazy {
        TacitIconImages::class.java.getResourceAsStream("/logo.png")?.use { ImageIO.read(it) }
    }

    private val cache = HashMap<Boolean, List<BufferedImage>>()

    /** All [sizes], with the red unread dot when [unread] is set. Cached: the dot does not depend on the count. */
    @Synchronized
    fun forUnread(unread: Boolean): List<BufferedImage> {
        val source = master ?: return emptyList()
        return cache.getOrPut(unread) {
            sizes.map { size ->
                val image = downscale(source, size)
                if (unread) drawUnreadDot(image)
                image
            }
        }
    }

    /** Same dot geometry as [TacitWindowIcon]: radius 1/8 of the icon, in the top right corner. */
    internal fun drawUnreadDot(image: BufferedImage) {
        val size = minOf(image.width, image.height).toDouble()
        val radius = size / 8.0
        val inset = radius * 1.15
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(0xFF0000)
            g.fill(Ellipse2D.Double(image.width - inset - radius, inset - radius, radius * 2, radius * 2))
        } finally {
            g.dispose()
        }
    }

    /**
     * Scales [source] to [size] x [size]: repeated exact 2:1 box averaging (in premultiplied alpha, so transparent
     * pixels do not bleed their colour) down to the smallest power-of-two multiple of [size], then one bilinear step
     * of at most 2:1. A thin line therefore ends up as a faint line instead of being dropped.
     */
    fun downscale(source: BufferedImage, size: Int): BufferedImage {
        require(size > 0) { "size must be positive" }
        var current = toPremultiplied(source)
        while (current.width / 2 >= size && current.height / 2 >= size) current = halve(current)

        val result = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = result.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.drawImage(current, 0, 0, size, size, null)
        } finally {
            g.dispose()
        }
        return result
    }

    private fun toPremultiplied(source: BufferedImage): BufferedImage {
        val copy = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB_PRE)
        val g = copy.createGraphics()
        try {
            g.drawImage(source, 0, 0, null)
        } finally {
            g.dispose()
        }
        return copy
    }

    /** Exact 2x2 average of a [BufferedImage.TYPE_INT_ARGB_PRE] image. */
    private fun halve(src: BufferedImage): BufferedImage {
        val w = src.width / 2
        val h = src.height / 2
        val sw = src.width
        val s = (src.raster.dataBuffer as DataBufferInt).data
        val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE)
        val d = (dst.raster.dataBuffer as DataBufferInt).data
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (2 * y) * sw + 2 * x
                val p0 = s[i]
                val p1 = s[i + 1]
                val p2 = s[i + sw]
                val p3 = s[i + sw + 1]
                var out = 0
                for (shift in intArrayOf(24, 16, 8, 0)) {
                    val sum = ((p0 ushr shift) and 0xFF) + ((p1 ushr shift) and 0xFF) +
                        ((p2 ushr shift) and 0xFF) + ((p3 ushr shift) and 0xFF)
                    out = out or (((sum + 2) / 4) shl shift)
                }
                d[y * w + x] = out
            }
        }
        return dst
    }
}
