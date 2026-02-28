package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.files.toImageBitmap
import de.connect2x.trixnity.messenger.compose.view.pointerMoveFilter
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.generated.resources.Res
import org.fuchss.matrix.tacit.generated.resources.tacit
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun GuildRail(
    guilds: List<GuildEntry>,
    guildAvatars: Map<String, ByteArray?>,
    selectedGuild: GuildEntry?,
    dmUnreadCount: Int,
    guildUnreadCounts: Map<String, Int>,
    onSelectGuild: (GuildEntry?) -> Unit,
    onCreateGuild: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(84.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(tacitRailGradientStart, tacitRailGradientMiddle, tacitRailGradientEnd)
                )
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GuildPill(
            label = null,
            selected = selectedGuild == null,
            onClick = { onSelectGuild(null) },
            dmIndicator = false, // No small icon at the bottom right for now :D
            isTacitHome = true,
            unreadCount = dmUnreadCount,
        )

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(guilds, key = { _, g -> "${g.userId.full}:${g.roomId.full}" }) { _, guild ->
                GuildPill(
                    label = guild.guildLabel(),
                    selected = selectedGuild?.roomId == guild.roomId && selectedGuild.userId == guild.userId,
                    onClick = { onSelectGuild(guild) },
                    avatarImage = guildAvatars[guild.key()],
                    unreadCount = guildUnreadCounts[guild.key()] ?: 0,
                )
            }
        }

        GuildCreateButton(onClick = onCreateGuild)
    }
}

@Composable
private fun GuildCreateButton(onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(52.dp)
            .clip(CircleShape)
            .background(if (hovered) tacitRailButtonHoverBackground else tacitRailButtonBackground)
            .pointerMoveFilter(
                onEnter = {
                    hovered = true
                    true
                },
                onExit = {
                    hovered = false
                    true
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Create guild",
            tint = if (hovered) Color.White else accentColor
        )
    }
}

@Composable
private fun GuildPill(
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
    symbol: String? = null,
    dmIndicator: Boolean = false,
    isTacitHome: Boolean = false,
    avatarImage: ByteArray? = null,
    unreadCount: Int = 0,
) {
    var hovered by remember { mutableStateOf(false) }
    val pillShape = if (selected) RoundedCornerShape(15.dp) else CircleShape

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (selected) 34.dp else 10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) Color.White else Color.Transparent)
        )

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(pillShape)
                    .background(
                        when {
                            selected -> tacitActionPrimaryBackground
                            hovered -> tacitRailButtonHoverBackground
                            else -> tacitRailButtonBackground
                        }
                    )
                    .pointerMoveFilter(
                        onEnter = {
                            hovered = true
                            true
                        },
                        onExit = {
                            hovered = false
                            true
                        },
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (isTacitHome) {
                    Image(
                        painter = painterResource(Res.drawable.tacit),
                        contentDescription = "Tacit",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(if (selected) RoundedCornerShape(13.dp) else CircleShape),
                    )
                } else if (avatarImage != null && avatarImage.toImageBitmap() != null) {

                    Image(
                        bitmap = avatarImage.toImageBitmap()!!,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(if (selected) RoundedCornerShape(13.dp) else CircleShape),
                    )
                } else {
                    Text(
                        text = symbol ?: label.orEmpty(),
                        color = Color.White,
                        style = if (symbol != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                        fontWeight = if (symbol != null) FontWeight.ExtraBold else FontWeight.SemiBold,
                    )
                }
                if (dmIndicator) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(tacitPanel),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Direct messages",
                            tint = tacitRailIconTint,
                            modifier = Modifier.size(9.dp),
                        )
                    }
                }
            }
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 1.dp, y = (-1).dp)
                        .clip(CircleShape)
                        .background(tacitBadgeSuccessBackground)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        color = tacitBadgeSuccessContent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
