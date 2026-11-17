package org.fuchss.matrix.tacit.views

import org.fuchss.matrix.tacit.ui.TacitAlpha
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.viewmodel.util.EventReactions
import de.connect2x.trixnity.messenger.viewmodel.util.ReactionStatus
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitShapes

/**
 * Whether the own account has a reaction here that a click should redact. A reaction whose redaction is already pending
 * counts as removed, so a second click reacts again instead of redacting twice.
 */
internal fun EventReactions.ByReactionsInfo.reactedByMe(): Boolean =
    reactions.any {
        when (it.status) {
            ReactionStatus.Sent, ReactionStatus.Pending, is ReactionStatus.SentError -> true
            ReactionStatus.FromOtherAccount, ReactionStatus.RedactionPending -> false
        }
    }

/** The text shown for [key]. Image reactions (custom emoji) use their key as `mxc://` URI; show the shortcode instead. */
internal fun EventReactions.ByReactionsInfo.label(key: String): String =
    if (key.startsWith("mxc://")) shortcode ?: key else key

@Composable
internal fun TacitMessageReactions(
    modifier: Modifier = Modifier,
    reactionEntries: List<Map.Entry<String, EventReactions.ByReactionsInfo>>,
    isOwnMessage: Boolean,
    onToggleReaction: (reaction: String, reactedByMe: Boolean) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactionEntries.forEach { (reaction, info) ->
            val reactedByMe = info.reactedByMe()
            val reactedByNames = info.reactions.joinToString { it.sender.name }
            TacitReactionChip(
                reaction = info.label(reaction),
                count = info.reactions.size,
                selected = reactedByMe,
                reactedByNames = reactedByNames,
                onClick = { onToggleReaction(reaction, reactedByMe) },
            )
        }
    }
}

@Composable
private fun TacitReactionChip(
    reaction: String,
    count: Int,
    selected: Boolean,
    reactedByNames: String,
    onClick: () -> Unit,
) {
    Tooltip(tooltip = { Text(reactedByNames) }) {
        Box(
            modifier = Modifier
                .clip(TacitShapes.compact)
                .background(
                    if (selected) MaterialTheme.tacit.accent.copy(alpha = 0.35f).compositeOver(MaterialTheme.tacit.surfaceAlt)
                    else MaterialTheme.tacit.surfaceAlt,
                    TacitShapes.compact,
                )
                .border(
                    1.dp,
                    if (selected) MaterialTheme.tacit.accent else MaterialTheme.tacit.border.copy(alpha = TacitAlpha.borderEmphasis).compositeOver(MaterialTheme.tacit.surfaceAlt),
                    TacitShapes.compact,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reaction,
                    color = if (selected) MaterialTheme.tacit.accent else MaterialTheme.tacit.text,
                )
                Text(
                    text = count.toString(),
                    color = if (selected) MaterialTheme.tacit.accent else MaterialTheme.tacit.textMuted,
                )
            }
        }
    }
}
