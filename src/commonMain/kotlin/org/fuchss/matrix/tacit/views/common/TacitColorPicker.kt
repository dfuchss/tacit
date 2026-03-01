package org.fuchss.matrix.tacit.views.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedIconButton
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedSlider
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TacitColorPicker(
    heading: String,
    resetTooltip: String,
    defaultColor: Color,
    color: Color,
    onColorSelected: (Color) -> Unit,
    hueLabel: String = "Hue",
    saturationLabel: String = "Saturation",
    brightnessLabel: String = "Brightness",
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }
    var isInteracting by remember { mutableStateOf(false) }

    LaunchedEffect(color, isInteracting) {
        if (isInteracting) return@LaunchedEffect
        val hsv = color.toHsv()
        hue = hsv.hue
        saturation = hsv.saturation
        value = hsv.value
    }

    val currentColor = hsvToColor(hue, saturation, value)
    val sliderStyle = MaterialTheme.components.slider.let {
        it.copy(colors = it.colors.copy(thumbColor = currentColor))
    }

    Text(
        text = heading,
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .background(currentColor, RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = currentColor.toHexRgb(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Tooltip({ Text(resetTooltip) }) {
            ThemedIconButton(
                style = MaterialTheme.components.primaryIconButton,
                onClick = {
                    val hsv = defaultColor.toHsv()
                    hue = hsv.hue
                    saturation = hsv.saturation
                    value = hsv.value
                    isInteracting = false
                    onColorSelected(defaultColor)
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = resetTooltip,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text(hueLabel, style = MaterialTheme.typography.labelMedium)
    ThemedSlider(
        value = hue,
        onValueChange = {
            isInteracting = true
            hue = it
            onColorSelected(hsvToColor(hue, saturation, value))
        },
        onValueChangeFinished = { isInteracting = false },
        valueRange = 0f..360f,
        style = sliderStyle,
        track = { HueTrack() },
    )

    Spacer(Modifier.height(8.dp))
    Text(saturationLabel, style = MaterialTheme.typography.labelMedium)
    ThemedSlider(
        value = saturation,
        onValueChange = {
            isInteracting = true
            saturation = it
            onColorSelected(hsvToColor(hue, saturation, value))
        },
        onValueChangeFinished = { isInteracting = false },
        valueRange = 0f..1f,
        style = sliderStyle,
        track = {
            GradientTrack(
                start = hsvToColor(hue, 0f, value),
                end = hsvToColor(hue, 1f, value),
            )
        },
    )

    Spacer(Modifier.height(8.dp))
    Text(brightnessLabel, style = MaterialTheme.typography.labelMedium)
    ThemedSlider(
        value = value,
        onValueChange = {
            isInteracting = true
            value = it
            onColorSelected(hsvToColor(hue, saturation, value))
        },
        onValueChangeFinished = { isInteracting = false },
        valueRange = 0f..1f,
        style = sliderStyle,
        track = {
            GradientTrack(
                start = Color.Black,
                end = hsvToColor(hue, saturation, 1f),
            )
        },
    )
}

@Composable
private fun HueTrack() {
    GradientTrack(
        brush = Brush.horizontalGradient(
            listOf(
                hsvToColor(0f, 1f, 1f),
                hsvToColor(60f, 1f, 1f),
                hsvToColor(120f, 1f, 1f),
                hsvToColor(180f, 1f, 1f),
                hsvToColor(240f, 1f, 1f),
                hsvToColor(300f, 1f, 1f),
                hsvToColor(360f, 1f, 1f),
            )
        )
    )
}

@Composable
private fun GradientTrack(
    start: Color? = null,
    end: Color? = null,
    brush: Brush? = null,
    modifier: Modifier = Modifier.height(4.dp),
) {
    var height by remember { mutableFloatStateOf(0f) }
    val actualBrush = brush ?: Brush.horizontalGradient(listOf(start ?: Color.Black, end ?: Color.White))
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { height = it.size.height.toFloat() }
    ) {
        val stroke = if (height <= 0f) size.height else height
        drawLine(
            brush = actualBrush,
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

private fun Color.toHsv(): HsvColor {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    val saturation = if (max == 0f) 0f else delta / max
    val value = max
    return HsvColor(hue, saturation, value)
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val c = value * saturation
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = value - c

    val (rPrime, gPrime, bPrime) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    return Color(
        red = (rPrime + m).coerceIn(0f, 1f),
        green = (gPrime + m).coerceIn(0f, 1f),
        blue = (bPrime + m).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

private fun Color.toHexRgb(): String {
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return "#${r.toHex2()}${g.toHex2()}${b.toHex2()}"
}

private fun Int.toHex2(): String = this.toString(16).uppercase().padStart(2, '0')
