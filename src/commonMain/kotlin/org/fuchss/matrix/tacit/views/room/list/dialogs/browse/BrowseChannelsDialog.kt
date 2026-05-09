package org.fuchss.matrix.tacit.views.room.list.dialogs.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.*
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitCardSurface
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.SpaceChannelStatus
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitDialogHeroCard

@Composable
internal fun BrowseChannelsDialog(
    guildName: String,
    channels: List<SpaceChannelEntry>,
    canJoinChannels: Boolean,
    joiningRoomId: RoomId?,
    onDismiss: () -> Unit,
    onJoinChannel: (SpaceChannelEntry) -> Unit,
) {
    val i18n = DI.get<TacitI18nView>()
    ThemedModalDialog(onDismissRequest = onDismiss) {
        ModalDialogHeader {
            Text(i18n.tacitBrowseRoomsTitle())
        }
        ModalDialogContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TacitDialogHeroCard(
                    eyebrow = guildName,
                    title = i18n.tacitDiscoverAndJoinRooms(),
                )
                if (!canJoinChannels) {
                    Text(
                        i18n.tacitNoAccountForGuild(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (channels.isEmpty()) {
                    Text(
                        i18n.tacitNoDiscoverableRooms(),
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
                            TacitCardSurface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(0.dp),
                                shape = TacitShapes.card,
                                backgroundColor = tacitSurface,
                                borderColor = tacitBorder.copy(alpha = 0.7f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.displayName,
                                            color = tacitText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = channel.status.toDisplayText(i18n),
                                            color = tacitTextMuted,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    if (channel.isJoined) {
                                        StatusChip(i18n.tacitJoined(), accentColor)
                                    } else if (!channel.isJoinable) {
                                        StatusChip(channel.status.toDisplayText(i18n).uppercase(), tacitTextMuted)
                                    } else {
                                        ThemedButton(
                                            style = MaterialTheme.components.primaryButton,
                                            onClick = { onJoinChannel(channel) },
                                            enabled = canJoinChannels && joiningRoomId == null,
                                        ) {
                                            Text(if (joiningRoomId == channel.roomId) i18n.tacitJoiningInProgress() else i18n.roomListJoin())
                                        }
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
                Text(i18n.commonClose())
            }
        }
    }
}

private fun SpaceChannelStatus.toDisplayText(i18n: TacitI18nView): String = when (this) {
    SpaceChannelStatus.JOINED -> i18n.tacitJoined()
    SpaceChannelStatus.INVITED -> i18n.tacitInvited()
    SpaceChannelStatus.KNOCKING -> i18n.tacitKnocking()
    SpaceChannelStatus.LEFT -> i18n.tacitLeft()
    SpaceChannelStatus.NOT_JOINED -> i18n.tacitNotJoined()
    SpaceChannelStatus.UNKNOWN -> i18n.tacitUnknown()
}

@Composable
private fun StatusChip(
    text: String,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(TacitShapes.pill)
            .background(tacitSurfaceAlt)
            .border(1.dp, tacitBorder.copy(alpha = 0.55f), TacitShapes.pill)
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
