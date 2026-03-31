package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.viewmodel.util.EventReactions
import org.fuchss.matrix.tacit.*

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
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) tacitAccent(0.14f) else tacitSurfaceAlt,
                    RoundedCornerShape(10.dp),
                )
                .border(
                    0.8.dp,
                    if (selected) accentColor else tacitBorder.copy(alpha = 0.65f),
                    RoundedCornerShape(10.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 2.dp),
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
