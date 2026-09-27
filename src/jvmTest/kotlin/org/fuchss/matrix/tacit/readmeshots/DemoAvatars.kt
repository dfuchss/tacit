package org.fuchss.matrix.tacit.readmeshots

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle

/**
 * Generates the demo users' avatars in code (a gradient-filled rounded square with the user's initial) so the
 * generator stays self-contained and no stock images need to be committed.
 */
internal object DemoAvatars {
    private const val SIZE = 256

    fun png(initial: String, colorFrom: Int, colorTo: Int): ByteArray {
        val surface = Surface.makeRasterN32Premul(SIZE, SIZE)
        val canvas = surface.canvas
        val size = SIZE.toFloat()
        Paint().use { paint ->
            paint.isAntiAlias = true
            paint.shader = Shader.makeLinearGradient(0f, 0f, size, size, intArrayOf(colorFrom, colorTo))
            canvas.drawRRect(RRect.makeXYWH(0f, 0f, size, size, size * 0.22f), paint)
        }
        Paint().use { paint ->
            paint.isAntiAlias = true
            paint.color = 0x33FFFFFF
            canvas.drawCircle(size * 0.78f, size * 0.22f, size * 0.30f, paint)
        }
        val font = Font(FontMgr.default.legacyMakeTypeface("", FontStyle.BOLD), size * 0.58f)
        val line = TextLine.make(initial, font)
        Paint().use { paint ->
            paint.isAntiAlias = true
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawTextLine(line, (size - line.width) / 2f, size / 2f + line.capHeight / 2f, paint)
        }
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
    }
}
