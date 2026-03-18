package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.common.EmojiSelector
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedDropdownMenu
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedDropdownMenuItem
import org.fuchss.matrix.tacit.tacitReactionPickerButtonBackground
import org.fuchss.matrix.tacit.tacitReactionPickerButtonBorder
import org.fuchss.matrix.tacit.tacitText

internal val tacitQuickReactionShortcuts = listOf("👍", "❤️", "😂", "🎉", "🔥", "🚀", "👀", "✅", "🤔", "😮", "😢", "🙌")

internal class TacitMessageActionMenuEntry(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    private val action: () -> Unit,
) {
    operator fun invoke() = action()
}

@Composable
internal fun tacitMessageActionMenuEntries(
    i18n: I18nView,
    canReply: Boolean,
    canEdit: Boolean,
    canRedact: Boolean,
    canReport: Boolean,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onShowInfo: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
): List<TacitMessageActionMenuEntry> {
    val deleteColor = MaterialTheme.colorScheme.error
    return buildList {
        if (canReply) add(
            TacitMessageActionMenuEntry(
                i18n.replyMessage(),
                Icons.AutoMirrored.Outlined.Reply, tacitText, onReply
            )
        )
        if (canEdit) add(TacitMessageActionMenuEntry(i18n.editMessage(), Icons.Outlined.Edit, tacitText, onEdit))
        add(TacitMessageActionMenuEntry(i18n.infoMessage(), Icons.Outlined.Info, tacitText, onShowInfo))
        if (canReport) add(
            TacitMessageActionMenuEntry(
                i18n.reportMessage(),
                Icons.Outlined.Report,
                tacitText,
                onReport
            )
        )
        if (canRedact) add(
            TacitMessageActionMenuEntry(
                i18n.redactMessage(),
                Icons.Outlined.Delete,
                deleteColor,
                onDelete
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
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tacitReactionPickerButtonBackground, RoundedCornerShape(10.dp))
            .border(1.dp, tacitReactionPickerButtonBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, color = tacitText)
    }
}

@Composable
internal fun TacitQuickActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(tacitReactionPickerButtonBackground, RoundedCornerShape(10.dp))
            .border(1.dp, tacitReactionPickerButtonBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = tacitText)
    }
}
