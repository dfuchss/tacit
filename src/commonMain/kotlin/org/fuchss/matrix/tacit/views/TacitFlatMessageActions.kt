package org.fuchss.matrix.tacit.views

import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.buttonPointerModifier
import de.connect2x.trixnity.messenger.compose.view.common.EmojiSelector
import de.connect2x.trixnity.messenger.compose.view.common.modifier.focusHighlighting
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedDropdownMenu
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedDropdownMenuItem
import org.fuchss.matrix.tacit.tacit
import org.fuchss.matrix.tacit.ui.TacitShapes

internal val tacitQuickReactionShortcuts = listOf("👍", "❤️", "😂", "🎉", "🔥", "🚀", "👀", "✅", "🤔", "😮", "😢", "\u2B55")

internal class TacitMessageActionMenuEntry(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    private val action: () -> Unit,
) {
    operator fun invoke() = action()
}

/** Which entries the message action menu offers. */
internal data class TacitMessageActionAvailability(
    val canReply: Boolean,
    val canEdit: Boolean,
    val canRedact: Boolean,
    val canReport: Boolean,
    val canCopy: Boolean,
    val canRetrySend: Boolean,
    val canAbortSend: Boolean,
)

/** What each message action menu entry does when picked. */
internal class TacitMessageActionCallbacks(
    val onReply: () -> Unit,
    val onEdit: () -> Unit,
    val onShowInfo: () -> Unit,
    val onReport: () -> Unit,
    val onDelete: () -> Unit,
    val onCopy: () -> Unit,
    val onRetrySend: () -> Unit,
    val onAbortSend: () -> Unit,
)

@Composable
internal fun tacitMessageActionMenuEntries(
    i18n: I18nView,
    availability: TacitMessageActionAvailability,
    callbacks: TacitMessageActionCallbacks,
): List<TacitMessageActionMenuEntry> {
    val deleteColor = MaterialTheme.colorScheme.error
    return buildList {
        // Recovery of a message that never left the device comes first: for an outbox element the
        // rest of this menu is almost empty (no event id -> no reply/edit/redact/report), and a
        // stuck message is the only thing the user wants to act on.
        if (availability.canRetrySend) add(
            TacitMessageActionMenuEntry(
                i18n.retrySendMessage(),
                Icons.Outlined.Refresh,
                MaterialTheme.tacit.accent,
                callbacks.onRetrySend
            )
        )
        if (availability.canAbortSend) add(
            TacitMessageActionMenuEntry(
                i18n.abortSendMessage(),
                Icons.Outlined.Cancel,
                deleteColor,
                callbacks.onAbortSend
            )
        )
        if (availability.canReply) add(
            TacitMessageActionMenuEntry(
                i18n.replyMessage(),
                Icons.AutoMirrored.Outlined.Reply, MaterialTheme.tacit.text, callbacks.onReply
            )
        )
        if (availability.canEdit) add(TacitMessageActionMenuEntry(i18n.editMessage(), Icons.Outlined.Edit, MaterialTheme.tacit.text, callbacks.onEdit))
        if (availability.canCopy) add(
            TacitMessageActionMenuEntry(
                i18n.commonCopy(),
                Icons.Outlined.ContentCopy,
                MaterialTheme.tacit.text,
                callbacks.onCopy
            )
        )
        add(TacitMessageActionMenuEntry(i18n.infoMessage(), Icons.Outlined.Info, MaterialTheme.tacit.text, callbacks.onShowInfo))
        if (availability.canReport) add(
            TacitMessageActionMenuEntry(
                i18n.reportMessage(),
                Icons.Outlined.Report,
                MaterialTheme.tacit.text,
                callbacks.onReport
            )
        )
        if (availability.canRedact) add(
            TacitMessageActionMenuEntry(
                i18n.redactMessage(),
                Icons.Outlined.Delete,
                deleteColor,
                callbacks.onDelete
            )
        )
    }
}

@Composable
internal fun TacitMessageActionDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<TacitMessageActionMenuEntry>,
    additionalContextActions: @Composable ColumnScope.(onClose: () -> Unit) -> Unit,
) {
    ThemedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 4.dp),
    ) {
        actions.forEach { action ->
            TacitMessageActionDropdownItem(
                label = action.label,
                icon = action.icon,
                color = action.color,
                onClick = action::invoke,
            )
        }
        additionalContextActions { onDismiss() }
    }
}

@Composable
private fun TacitMessageActionDropdownItem(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    ThemedDropdownMenuItem(
        text = { Text(label, color = color) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null, tint = color) },
    )
}

@Composable
internal fun TacitReactionPickerMenu(
    expanded: Boolean,
    alignEnd: Boolean,
    yOffset: Dp = 4.dp,
    onDismiss: () -> Unit,
    onSelectReaction: (String) -> Unit,
) {
    ThemedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(if (alignEnd) (-8).dp else 0.dp, yOffset),
        modifier = Modifier.size(320.dp, 240.dp),
    ) {
        EmojiSelector(
            modifier = Modifier.size(320.dp, 240.dp),
            onTextAdded = { emoji ->
                onSelectReaction(emoji)
                onDismiss()
            },
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun TacitQuickReactionPickerMenu(
    expanded: Boolean,
    alignEnd: Boolean,
    quickReactions: List<String>,
    onDismiss: () -> Unit,
    onSelectReaction: (String) -> Unit,
) {
    ThemedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(if (alignEnd) (-8).dp else 0.dp, 4.dp),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        quickReactions.chunked(6).forEach { rowReactions ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowReactions.forEach { reaction ->
                    TacitQuickReactionPickerButton(
                        emoji = reaction,
                        onClick = { onSelectReaction(reaction) },
                    )
                }
                repeat(6 - rowReactions.size) {
                    Spacer(Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun TacitQuickReactionPickerButton(
    emoji: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(TacitShapes.compact)
            .background(MaterialTheme.tacit.surface, TacitShapes.compact)
            .border(TacitSizes.borderWidth, MaterialTheme.tacit.border, TacitShapes.compact)
            .tacitInteractive(onClickLabel = emoji, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, color = MaterialTheme.tacit.text)
    }
}

@Composable
internal fun TacitQuickActionButton(
    label: String? = null,
    icon: ImageVector? = null,
    /** Spoken/click label; defaults to [label], which is not meaningful for emoji-only buttons. */
    description: String? = label,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(TacitShapes.compact)
            .background(MaterialTheme.tacit.surface, TacitShapes.compact)
            .border(TacitSizes.borderWidth, MaterialTheme.tacit.border, TacitShapes.compact)
            .tacitInteractive(onClickLabel = description, onClick = onClick)
            // unchanged: keeps the button at its current visual size
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = description, tint = MaterialTheme.tacit.text)
        } else {
            Text(label ?: "?", color = MaterialTheme.tacit.text)
        }
    }
}

/**
 * Makes a bespoke `Box`-based control behave like a real button: a hand cursor on pointer devices
 * (upstream [buttonPointerModifier], the multiplatform wrapper around `pointerHoverIcon`) and a
 * visible focus ring driven by upstream [focusHighlighting]. `clickable` with an explicit
 * interaction source also makes the control focusable and keyboard-activatable, and feeds the very
 * same source that the focus ring observes.
 */
@Composable
private fun Modifier.tacitInteractive(onClickLabel: String? = null, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .focusHighlighting(interactionSource, color = MaterialTheme.tacit.accent, shape = TacitShapes.compact)
        .buttonPointerModifier()
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}
