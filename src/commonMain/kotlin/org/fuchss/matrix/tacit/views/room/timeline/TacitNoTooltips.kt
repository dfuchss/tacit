package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.connect2x.trixnity.messenger.compose.view.theme.ComponentStyles
import de.connect2x.trixnity.messenger.compose.view.theme.MaterialThemeComponents
import de.connect2x.trixnity.messenger.compose.view.theme.ThemeComponents
import de.connect2x.trixnity.messenger.compose.view.theme.components
import kotlin.time.Duration

private class NoTooltipComponents(private val base: ComponentStyles) : ThemeComponents {
    @Composable
    override fun create(): ComponentStyles = base.copy(
        tooltip = base.tooltip.copy(longPressDelay = Duration.INFINITE, hoverShowDelay = Duration.INFINITE),
    )
}

/**
 * Suppresses the hover/focus/long-press tooltips of everything inside [content].
 *
 * Upstream wraps the text of system events and of the date pills in a tooltip that merely repeats the visible text.
 * Those views take their tooltip delays from the theme's `TooltipStyle` and cannot be configured per call, so the
 * delays are set to infinity for this subtree. Only use it around content without meaningful tooltips.
 */
@Composable
internal fun WithoutTooltips(content: @Composable () -> Unit) {
    val components = MaterialTheme.components
    val noTooltips = remember(components) { NoTooltipComponents(components) }
    MaterialThemeComponents(noTooltips, content)
}
