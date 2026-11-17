package org.fuchss.matrix.tacit.views

import org.fuchss.matrix.tacit.ui.TacitAlpha
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.abi.TrixnityMessengerPrivateApi
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.compose.view.files.rememberImageBitmapOrNull
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

/**
 * The reaction itself: a custom emote (an image reaction whose image upstream already loads into
 * [EventReactions.ByReactionsInfo.image]) is drawn as the image at text size, described by its [label]. Until the image
 * is there, or if it cannot be decoded, the [label] (shortcode, else the key) is shown as text, like upstream's
 * `MessageReactionDisplay`.
 */
@OptIn(TrixnityMessengerPrivateApi::class)
@Composable
internal fun TacitReactionContent(reaction: String, info: EventReactions.ByReactionsInfo, color: Color) {
    val label = info.label(reaction)
    val imageBytes = info.image?.collectAsState()?.value
    val bitmap = rememberImageBitmapOrNull(imageBytes, maxSize = ReactionImageDecodeSize)
    if (bitmap != null) {
        val size = with(LocalDensity.current) { LocalTextStyle.current.fontSize.takeIf { it.isSp }?.toDp() } ?: 16.dp
        Image(
            bitmap = bitmap,
            contentDescription = label,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(text = label, color = color)
    }
}

/** Upstream decodes reaction images at the same bound. */
private val ReactionImageDecodeSize = IntSize(64, 64)

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
                reaction = reaction,
                info = info,
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
    info: EventReactions.ByReactionsInfo,
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
                TacitReactionContent(
                    reaction = reaction,
                    info = info,
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
