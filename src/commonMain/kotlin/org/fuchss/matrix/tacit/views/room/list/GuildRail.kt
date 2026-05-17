package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.files.toImageBitmap
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.pointerMoveFilter
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.generated.resources.Res
import org.fuchss.matrix.tacit.generated.resources.tacit
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.ui.TacitSpacing
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private val guildPillSize: Dp = 52.dp
private val guildPillSpacing: Dp = 10.dp

@Composable
internal fun GuildRail(
    guilds: List<GuildEntry>,
    guildAvatars: Map<String, ByteArray?>,
    selectedGuild: GuildEntry?,
    dmUnreadCount: Int,
    guildUnreadCounts: Map<String, Int>,
    onSelectGuild: (GuildEntry?) -> Unit,
    onReorderGuild: (fromIndex: Int, toIndex: Int) -> Unit,
    onCreateGuild: () -> Unit,
) {
    val density = LocalDensity.current
    val guildStepPx = remember(density) { with(density) { (guildPillSize + guildPillSpacing).toPx() } }
    var draggingGuildKey by remember { mutableStateOf<String?>(null) }
    var dragFromIndex by remember { mutableStateOf<Int?>(null) }
    var dropIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    fun resetDragState() {
        draggingGuildKey = null
        dragFromIndex = null
        dropIndex = null
        dragOffsetY = 0f
    }

    val hintBeforeIndex = remember(dragFromIndex, dropIndex, guilds) {
        val from = dragFromIndex
        val to = dropIndex
        if (from == null || to == null || guilds.isEmpty() || from !in guilds.indices || to !in guilds.indices || from == to) null
        else if (to > from) (to + 1).coerceAtMost(guilds.size)
        else to
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(84.dp)
            .clip(TacitShapes.rail)
            .background(
                Brush.verticalGradient(
                    listOf(tacitBackground, tacitSurface, tacitBackground)
                )
            )
            .border(1.dp, tacitBorder, TacitShapes.rail)
            .padding(vertical = 12.dp),
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

        Spacer(Modifier.height(TacitSpacing.paneGap))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(guildPillSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(guilds, key = { _, g -> "${g.userId.full}:${g.roomId.full}" }) { index, guild ->
                val guildKey = guild.key()
                if (hintBeforeIndex == index) {
                    GuildDropHint()
                }
                Box(
                    modifier = Modifier
                        .zIndex(if (draggingGuildKey == guildKey) 5f else 0f)
                        .graphicsLayer {
                            if (draggingGuildKey == guildKey) {
                                translationY = dragOffsetY
                                scaleX = 1.04f
                                scaleY = 1.04f
                                shadowElevation = with(density) { 14.dp.toPx() }
                            }
                        }
                        .alpha(if (draggingGuildKey == guildKey) 0.96f else 1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectGuild(guild) },
                        )
                        .pointerInput(guilds, guildKey, index) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingGuildKey = guildKey
                                    dragFromIndex = index
                                    dropIndex = index
                                    dragOffsetY = 0f
                                },
                                onDragEnd = {
                                    val from = dragFromIndex
                                    val to = dropIndex
                                    resetDragState()
                                    if (from != null && to != null && from in guilds.indices && to in guilds.indices && from != to) {
                                        onReorderGuild(from, to)
                                    }
                                },
                                onDragCancel = {
                                    resetDragState()
                                },
                                onDrag = { change, dragAmount ->
                                    if (draggingGuildKey != guildKey) return@detectDragGestures
                                    change.consume()
                                    dragOffsetY += dragAmount.y

                                    val from = dragFromIndex ?: return@detectDragGestures
                                    val newDropIndex = (from + (dragOffsetY / guildStepPx).roundToInt())
                                        .coerceIn(0, guilds.lastIndex)
                                    if (dropIndex != newDropIndex) {
                                        dropIndex = newDropIndex
                                    }
                                },
                            )
                        }
                ) {
                    GuildPill(
                        label = guild.guildLabel(),
                        selected = selectedGuild?.roomId == guild.roomId && selectedGuild.userId == guild.userId,
                        onClick = null,
                        avatarImage = guildAvatars[guild.key()],
                        unreadCount = guildUnreadCounts[guild.key()] ?: 0,
                    )
                }
            }
            if (hintBeforeIndex == guilds.size) {
                item("guild-drop-hint-end") {
                    GuildDropHint()
                }
            }
        }

        GuildCreateButton(onClick = onCreateGuild)
    }
}

@Composable
private fun GuildDropHint() {
    val pulseAlpha by rememberInfiniteTransition(label = "guildDropHintPulse").animateFloat(
        initialValue = 0.38f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "guildDropHintAlpha",
    )

    Box(
        modifier = Modifier
            .size(guildPillSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(tacitSurface.copy(alpha = 0.35f))
                .border(width = 1.5.dp, color = accentColor.copy(alpha = pulseAlpha), shape = CircleShape),
        )
    }
}

@Composable
private fun GuildCreateButton(onClick: () -> Unit) {
    val i18n = DI.get<TacitI18nView>()
    var hovered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(top = TacitSpacing.compactGap)
            .size(guildPillSize)
            .clip(CircleShape)
            .background(if (hovered) tacitSurfaceAlt else tacitSurface)
            .border(1.dp, tacitBorder, CircleShape)
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
            contentDescription = i18n.tacitCreateGuildDescriptionIcon(),
            tint = if (hovered) Color.White else accentColor
        )
    }
}

@Composable
private fun GuildPill(
    label: String?,
    selected: Boolean,
    onClick: (() -> Unit)?,
    symbol: String? = null,
    dmIndicator: Boolean = false,
    isTacitHome: Boolean = false,
    avatarImage: ByteArray? = null,
    unreadCount: Int = 0,
) {
    val i18n = DI.get<TacitI18nView>()
    var hovered by remember { mutableStateOf(false) }
    val pillShape = if (selected) TacitShapes.selectedPill else TacitShapes.circle

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(guildPillSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
                .width(4.dp)
                .height(if (selected) 34.dp else 10.dp)
                .clip(TacitShapes.pill)
                .background(if (selected) Color.White else Color.Transparent)
        )

        Box(
            modifier = Modifier
                .size(guildPillSize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(pillShape)
                    .background(
                        when {
                            selected -> tacitAccent(0.25f)
                            hovered -> tacitSurfaceAlt
                            else -> tacitSurface
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) accentColor.copy(alpha = 0.65f) else tacitBorder.copy(alpha = 0.75f),
                        shape = pillShape,
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
                    .then(
                        if (onClick != null) Modifier.clickable(onClick = onClick)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isTacitHome) {
                    Image(
                        painter = painterResource(Res.drawable.tacit),
                        contentDescription = i18n.tacitAppName(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(if (selected) TacitShapes.control else TacitShapes.circle),
                    )
                } else if (avatarImage != null && avatarImage.toImageBitmap() != null) {

                    Image(
                        bitmap = avatarImage.toImageBitmap()!!,
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(if (selected) TacitShapes.control else TacitShapes.circle),
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
                            .background(tacitSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = i18n.tacitDirectMessagesDescription(),
                            tint = tacitText,
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
                        .background(accentColor)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        color = tacitOnAccent(accentColor),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
