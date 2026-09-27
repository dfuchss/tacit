package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.messenger.compose.view.buttonPointerModifier
import de.connect2x.trixnity.messenger.compose.view.common.modifier.focusHighlighting
import org.fuchss.matrix.tacit.accentColor
import org.fuchss.matrix.tacit.tacitText
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

internal fun dmInitials(name: String): String {
    val parts = name.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

internal fun dmPresenceColor(presence: Presence?): Color = when (presence) {
    Presence.ONLINE -> accentColor
    else -> Color.Transparent
}

/**
 * Accessible label for the presence ring drawn around a direct-message avatar.
 *
 * Returns `null` for every presence that is not rendered, so offline rooms stay silent instead of
 * adding a word to every row of a long list.
 */
internal fun dmPresenceLabel(presence: Presence?, i18n: TacitI18nView): String? = when (presence) {
    Presence.ONLINE -> i18n.presenceOnline()
    else -> null
}

/**
 * Hand cursor and keyboard focus ring for the hand-rolled (non `Themed*`) controls of the room list.
 *
 * Both behaviours come from the messenger library ([buttonPointerModifier] / [focusHighlighting]) so
 * that bespoke controls behave like the themed ones. Apply it directly after the `clickable` that
 * owns [interactionSource], otherwise the focus ring never lights up.
 */
@Composable
internal fun Modifier.tacitInteractive(
    interactionSource: MutableInteractionSource,
    shape: Shape,
    enabled: Boolean = true,
    focusColor: Color = tacitText,
): Modifier = this
    .focusHighlighting(interactionSource, color = focusColor, shape = shape)
    .buttonPointerModifier(enabled)
