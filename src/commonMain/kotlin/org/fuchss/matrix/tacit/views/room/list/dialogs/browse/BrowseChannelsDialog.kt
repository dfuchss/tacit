package org.fuchss.matrix.tacit.views.room.list.dialogs.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.*
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry

@Composable
internal fun BrowseChannelsDialog(
    guildName: String,
    channels: List<SpaceChannelEntry>,
    canJoinChannels: Boolean,
    joiningRoomId: RoomId?,
    onDismiss: () -> Unit,
    onJoinChannel: (SpaceChannelEntry) -> Unit,
) {
    ThemedModalDialog(onDismissRequest = onDismiss) {
        ModalDialogHeader {
            Text("Browse Rooms")
        }
        ModalDialogContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(tacitHeroGradientStart, tacitDialogGradientMiddle, tacitDialogGradientEnd)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = guildName,
                            style = MaterialTheme.typography.labelMedium,
                            color = tacitTextMuted,
                        )
                        Text(
                            text = "Discover and join rooms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tacitText,
                        )
                    }
                }
                if (!canJoinChannels) {
                    Text(
                        "No account available for this guild.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (channels.isEmpty()) {
                    Text(
                        "No discoverable rooms in this guild yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tacitTextMuted,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(channels, key = { _, channel -> channel.roomId.full }) { _, channel ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tacitPanel)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.displayName,
                                        color = tacitDialogChannelText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = channel.status,
                                        color = tacitTextMuted,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (channel.isJoined) {
                                    StatusChip("JOINED", accentColor)
                                } else if (!channel.isJoinable) {
                                    StatusChip(channel.status.uppercase(), tacitTextMuted)
                                } else {
                                    ThemedButton(
                                        style = MaterialTheme.components.primaryButton,
                                        onClick = { onJoinChannel(channel) },
                                        enabled = canJoinChannels && joiningRoomId == null,
                                    ) {
                                        Text(if (joiningRoomId == channel.roomId) "Joining..." else "Join")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ModalDialogFooter {
            ThemedButton(
                style = MaterialTheme.components.commonButton,
                onClick = onDismiss,
                enabled = joiningRoomId == null,
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tacitChipBackground)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
