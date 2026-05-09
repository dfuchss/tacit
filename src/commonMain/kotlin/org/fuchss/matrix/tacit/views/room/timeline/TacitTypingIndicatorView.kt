package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.room.timeline.TypingIndicatorView
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import org.fuchss.matrix.tacit.tacitBorder
import org.fuchss.matrix.tacit.tacitSurface
import org.fuchss.matrix.tacit.tacitText
import org.fuchss.matrix.tacit.tacitTextMuted
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitTimelineViewModel

class TacitTypingIndicatorView : TypingIndicatorView {
    @Composable
    override fun create(timelineViewModel: TimelineViewModel) {
        val tacitTimelineViewModel = timelineViewModel as? TacitTimelineViewModel
        val typingMembers = tacitTimelineViewModel?.typingMembers?.collectAsState()?.value.orEmpty()
        val typingText = tacitTimelineViewModel?.typingIndicatorText?.collectAsState()?.value
            ?: timelineViewModel.roomHeaderViewModel.usersTyping.collectAsState().value

        val showIndicator = !typingText.isNullOrBlank()
        AnimatedVisibility(
            visible = showIndicator,
            enter = fadeIn(animationSpec = tween(150)) + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 18.dp, top = 2.dp, bottom = 6.dp)
                    .clip(TacitShapes.card)
                    .background(tacitSurface.copy(alpha = 0.72f))
                    .border(1.dp, tacitBorder.copy(alpha = 0.45f), TacitShapes.card)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (typingMembers.isNotEmpty()) {
                    typingMembers.take(3).forEach { member ->
                        ThemedUserAvatar(
                            initials = member.displayName.typingInitials(),
                            image = member.avatarImage,
                            size = 20.dp,
                        )
                    }
                    if (typingMembers.size > 3) {
                        Text(
                            text = "+${typingMembers.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tacitTextMuted,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(tacitTextMuted.copy(alpha = 0.7f))
                    )
                }
                Text(
                    text = typingText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = tacitText,
                )
            }
        }
    }
}

private fun String.typingInitials(): String {
    val parts = split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
