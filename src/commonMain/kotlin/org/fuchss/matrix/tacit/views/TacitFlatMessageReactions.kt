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
        modifier = modifier
            .background(
                if (isOwnMessage) tacitAccent(0.14f) else tacitSurface,
                RoundedCornerShape(14.dp),
            )
            .border(1.dp, tacitBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactionEntries.forEach { (reaction, infos) ->
            val reactedByMe = infos.any { it.isMe }
            TacitReactionChip(
                reaction = reaction,
                count = infos.size,
                selected = reactedByMe,
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
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) tacitAccent(0.22f) else tacitSurface,
                RoundedCornerShape(11.dp),
            )
            .border(
                1.dp,
                if (selected) accentColor else tacitBorder,
                RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = reaction,
                color = if (selected) accentColor else tacitText,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) tacitAccent(0.22f) else tacitSurfaceAlt,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 5.dp, vertical = 0.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    color = if (selected) accentColor else tacitText,
                )
            }
        }
    }
}
