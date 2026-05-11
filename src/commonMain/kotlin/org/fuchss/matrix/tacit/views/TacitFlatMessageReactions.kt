package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.viewmodel.util.EventReactions
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitShapes

@Composable
internal fun TacitMessageReactions(
    modifier: Modifier = Modifier,
    reactionEntries: List<Map.Entry<String, Set<EventReactions.ByReactionInfo>>>,
    isOwnMessage: Boolean,
    onToggleReaction: (reaction: String, reactedByMe: Boolean) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactionEntries.forEach { (reaction, infos) ->
            val reactedByMe = infos.any { it.isMe }
            val reactedByNames = infos.joinToString { it.sender.name }
            TacitReactionChip(
                reaction = reaction,
                count = infos.size,
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
                    if (selected) accentColor.copy(alpha = 0.35f).compositeOver(tacitSurfaceAlt)
                    else tacitSurfaceAlt,
                    TacitShapes.compact,
                )
                .border(
                    1.dp,
                    if (selected) accentColor else tacitBorder.copy(alpha = 0.65f).compositeOver(tacitSurfaceAlt),
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
                    color = if (selected) accentColor else tacitText,
                )
                Text(
                    text = count.toString(),
                    color = if (selected) accentColor else tacitTextMuted,
                )
            }
        }
    }
}
