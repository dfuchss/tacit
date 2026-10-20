package org.fuchss.matrix.tacit

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TacitIconImagesTest {
    private fun stripes(size: Int): BufferedImage =
        BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { img ->
            for (y in 0 until size) for (x in 0 until size) {
                // One black pixel column in every 16, on white.
                img.setRGB(x, y, if (x % 16 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }

    @Test
    fun producesExactlyTheRequestedSizes() {
        val master = stripes(1024)
        for (size in TacitIconImages.sizes) {
            val image = TacitIconImages.downscale(master, size)
            assertEquals(size, image.width)
            assertEquals(size, image.height)
        }
    }

    @Test
    fun aThinLineIsAveragedInsteadOfDropped() {
        val image = TacitIconImages.downscale(stripes(1024), 16)
        // 1024 -> 16 is 64 source pixels per target pixel, with 4 black columns among them: 1/16 black.
        val gray = (0 until 16).map { image.getRGB(it, 8) and 0xFF }
        assertTrue(gray.all { it in 200..250 }, "expected faint grey in every column, got $gray")
    }

    @Test
    fun transparentPixelsDoNotBleedTheirColour() {
        val img = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 64) for (x in 0 until 32) img.setRGB(x, y, 0xFFFF0000.toInt())
        for (y in 0 until 64) for (x in 32 until 64) img.setRGB(x, y, 0x00000000)
        val out = TacitIconImages.downscale(img, 20)
        val left = out.getRGB(2, 10)
        assertEquals(0xFF, left ushr 24)
        assertEquals(0xFF0000, left and 0xFFFFFF)
        assertEquals(0, out.getRGB(17, 10) ushr 24)
    }

    @Test
    fun unreadDotIsDrawnInTheTopRight() {
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        TacitIconImages.drawUnreadDot(img)
        assertEquals(0xFFFF0000.toInt(), img.getRGB(32 - 5, 5))
        assertEquals(0, img.getRGB(5, 32 - 5))
    }
}
