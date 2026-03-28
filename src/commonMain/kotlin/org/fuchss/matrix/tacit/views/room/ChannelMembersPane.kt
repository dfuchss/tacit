package org.fuchss.matrix.tacit.views.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import org.fuchss.matrix.tacit.tacitBorder
import org.fuchss.matrix.tacit.tacitSurface
import org.fuchss.matrix.tacit.tacitSurfaceAlt
import org.fuchss.matrix.tacit.tacitText
import org.fuchss.matrix.tacit.tacitTextMuted
import org.fuchss.matrix.tacit.viewmodel.room.timeline.entry.ChannelMemberEntry
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.room.list.dialogs.direct.CreateDirectMessageDialog

@Composable
internal fun ChannelMembersPane(
    roomMembers: List<ChannelMemberEntry>,
    onMemberClick: (ChannelMemberEntry, onNeedsConfirmation: () -> Unit) -> Unit,
    onConfirmStartDirectMessage: (ChannelMemberEntry) -> Unit,
) {
    val i18n = DI.get<TacitI18nView>()
    var selectedMemberForDm by remember { mutableStateOf<ChannelMemberEntry?>(null) }

    Column(
        modifier = Modifier.Companion
            .fillMaxSize()
            .background(tacitSurface)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = i18n.tacitMembersTitle(roomMembers.size),
            color = tacitText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Companion.SemiBold,
        )

        if (roomMembers.isEmpty()) {
            Box(
                modifier = Modifier.Companion.fillMaxSize(),
                contentAlignment = Alignment.Companion.Center,
            ) {
                Text(
                    text = i18n.tacitNoMembersToShow(),
                    color = tacitTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.Companion.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(roomMembers, key = { it.userId.full }) { member ->
                    MemberRow(
                        member = member,
                        i18n = i18n,
                        clickable = !member.isSelf,
                        onClick = {
                            onMemberClick(member) {
                                selectedMemberForDm = member
                            }
                        },
                    )
                }
            }
        }
    }

    val selectedMember = selectedMemberForDm
    if (selectedMember != null) {
        CreateDirectMessageDialog(
            isStartingDirectMessage = false,
            canStartDirectMessage = true,
            canSearchUsers = false,
            searchUsers = { Result.success(emptyList()) },
            presetUserId = selectedMember.userId.full,
            presetDisplayName = selectedMember.displayName,
            onDismiss = { selectedMemberForDm = null },
            onStartDirectMessage = {
                selectedMemberForDm = null
                onConfirmStartDirectMessage(selectedMember)
            },
        )
    }
}

@Composable
private fun MemberRow(
    member: ChannelMemberEntry,
    i18n: TacitI18nView,
    clickable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tacitSurfaceAlt)
            .border(1.dp, tacitBorder, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Companion.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemedUserAvatar(
            initials = member.displayName.memberInitials(),
            image = member.avatarImage,
            size = 28.dp,
        )

        Column(modifier = Modifier.Companion.weight(1f)) {
            Text(
                text = if (member.isSelf) i18n.tacitYouSuffix(member.displayName) else member.displayName,
                color = tacitText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis,
            )
            Text(
                text = member.userId.full,
                color = tacitTextMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Companion.Ellipsis,
            )
        }
    }
}

private fun String.memberInitials(): String {
    val pieces = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        pieces.isEmpty() -> "?"
        pieces.size == 1 -> pieces.first().take(2).uppercase()
        else -> "${pieces[0].take(1)}${pieces[1].take(1)}".uppercase()
    }
}
