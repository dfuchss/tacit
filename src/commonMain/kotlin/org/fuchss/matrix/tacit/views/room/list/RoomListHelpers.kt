package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.messenger.compose.view.buttonPointerModifier
import de.connect2x.trixnity.messenger.compose.view.common.modifier.focusHighlighting
import org.fuchss.matrix.tacit.TacitTokens
import org.fuchss.matrix.tacit.tacit
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

internal fun dmPresenceColor(presence: Presence?, tokens: TacitTokens): Color = when (presence) {
    Presence.ONLINE -> tokens.accent
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
    focusColor: Color = MaterialTheme.tacit.text,
): Modifier = this
    .focusHighlighting(interactionSource, color = focusColor, shape = shape)
    .buttonPointerModifier(enabled)
