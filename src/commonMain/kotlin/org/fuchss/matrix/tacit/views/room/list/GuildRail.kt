package org.fuchss.matrix.tacit.views.room.list

import org.fuchss.matrix.tacit.ui.TacitAlpha
import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.abi.TrixnityMessengerPrivateApi
import de.connect2x.trixnity.messenger.compose.view.buttonPointerModifier
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.compose.view.files.decodeToImageBitmapOrNull
import de.connect2x.trixnity.messenger.compose.view.get
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.generated.resources.Res
import org.fuchss.matrix.tacit.generated.resources.tacit
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.ui.TacitSpacing
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitGuildRailState
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.room.list.guildDropHintIndex
import org.fuchss.matrix.tacit.viewmodel.room.list.guildDropIndex
import org.fuchss.matrix.tacit.viewmodel.room.list.isMove
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.jetbrains.compose.resources.imageResource

private val guildPillSize: Dp = 52.dp
private val guildPillSpacing: Dp = 10.dp

/** How far the unread badge pokes out above its pill; the guild list reserves it so its clip does not cut the badge. */
private val unreadBadgeOverhang: Dp = 1.dp

@Composable
internal fun GuildRail(
    state: TacitGuildRailState,
    onSelectGuild: (GuildEntry?) -> Unit,
    onReorderGuild: (guildKey: String, toIndex: Int) -> Unit,
    onCreateGuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val guilds = state.guilds
    val selectedGuild = state.selectedGuild
    // The drag gestures outlive list changes (they are keyed on the guild only), so they read the latest values.
    val currentGuildCount by rememberUpdatedState(guilds.size)
    val currentOnReorderGuild by rememberUpdatedState(onReorderGuild)
    val density = LocalDensity.current
    val guildStepPx = remember(density) { with(density) { (guildPillSize + guildPillSpacing).toPx() } }
    var draggingGuildKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    fun resetDragState() {
        draggingGuildKey = null
        dragOffsetY = 0f
    }

    // Derived from the dragged guild's key against the current list, so a guild list change mid-drag (a guild
    // joined, left or reordered elsewhere) cannot leave a stale index behind. Only changes at whole pill steps.
    val hintBeforeIndex by remember(guilds, guildStepPx) {
        derivedStateOf {
            val fromIndex = draggingGuildKey?.let { key -> guilds.indexOfFirst { it.key() == key } }?.takeIf { it >= 0 }
            val dropIndex = fromIndex?.let { guildDropIndex(it, dragOffsetY, guildStepPx, guilds.size) }
            guildDropHintIndex(fromIndex, dropIndex, guilds.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(TacitSizes.guildRailWidth)
            .clip(TacitShapes.rail)
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.tacit.background, MaterialTheme.tacit.surface, MaterialTheme.tacit.background)
                )
            )
            .border(TacitSizes.borderWidth, MaterialTheme.tacit.border, TacitShapes.rail)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GuildPill(
            label = null,
            selected = selectedGuild == null,
            onClick = { onSelectGuild(null) },
            dmIndicator = false, // No small icon at the bottom right for now :D
            isTacitHome = true,
            unreadCount = state.dmUnreadCount,
        )

        Spacer(Modifier.height(TacitSpacing.paneGap - unreadBadgeOverhang))

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = unreadBadgeOverhang),
            verticalArrangement = Arrangement.spacedBy(guildPillSpacing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(guilds, key = { _, g -> "${g.userId.full}:${g.roomId.full}" }) { index, guild ->
                val guildKey = guild.key()
                val guildName = guild.displayName?.ifBlank { null } ?: guild.roomId.full
                val pillInteractionSource = remember(guildKey) { MutableInteractionSource() }
                val currentIndex by rememberUpdatedState(index)
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
                            interactionSource = pillInteractionSource,
                            indication = null,
                            onClickLabel = guildName,
                            role = Role.Button,
                            onClick = { onSelectGuild(guild) },
                        )
                        // Drag-to-reorder has no static cue of its own: at least offer the hand
                        // cursor over the whole pill.
                        .buttonPointerModifier()
                        .pointerInput(guildKey) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingGuildKey = guildKey
                                    dragOffsetY = 0f
                                },
                                onDragEnd = {
                                    // The pill is translated from its current slot, so the drop counts from there;
                                    // the view model resolves the guild by its key against its own current list.
                                    val to = guildDropIndex(currentIndex, dragOffsetY, guildStepPx, currentGuildCount)
                                    resetDragState()
                                    if (isMove(currentIndex, to, currentGuildCount)) {
                                        currentOnReorderGuild(guildKey, to)
                                    }
                                },
                                onDragCancel = {
                                    resetDragState()
                                },
                                onDrag = { change, dragAmount ->
                                    if (draggingGuildKey != guildKey) return@detectDragGestures
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                },
                            )
                        }
                ) {
                    GuildPill(
                        label = guild.guildLabel(),
                        selected = selectedGuild?.roomId == guild.roomId && selectedGuild.userId == guild.userId,
                        onClick = null,
                        avatarImage = state.guildAvatars[guildKey],
                        unreadCount = state.guildUnreadCounts[guildKey] ?: 0,
                        interactionSource = pillInteractionSource,
                        showDragAffordance = guilds.size > 1 && draggingGuildKey == null,
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
                .background(MaterialTheme.tacit.surface.copy(alpha = 0.35f))
                .border(width = 1.5.dp, color = MaterialTheme.tacit.accent.copy(alpha = pulseAlpha), shape = CircleShape),
        )
    }
}

@Composable
private fun GuildCreateButton(onClick: () -> Unit) {
    val i18n = DI.get<TacitI18nView>()
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val label = i18n.tacitCreateGuildDescriptionIcon()

    Tooltip(tooltip = { Text(label) }) {
        Box(
            modifier = Modifier
                .padding(top = TacitSpacing.compactGap)
                .size(guildPillSize)
                .clip(CircleShape)
                .background(if (hovered) MaterialTheme.tacit.surfaceAlt else MaterialTheme.tacit.surface)
                .border(TacitSizes.borderWidth, MaterialTheme.tacit.border, CircleShape)
                .hoverable(hoverSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClickLabel = label,
                    role = Role.Button,
                    onClick = onClick,
                )
                .tacitInteractive(interactionSource = interactionSource, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = label,
                tint = if (hovered) MaterialTheme.tacit.onAccentFixed else MaterialTheme.tacit.accent
            )
        }
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
    interactionSource: MutableInteractionSource? = null,
    showDragAffordance: Boolean = false,
) {
    val i18n = DI.get<TacitI18nView>()
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    // When the pill itself is not clickable, the caller owns the click (and therefore the focus);
    // reuse its interaction source so the focus ring is drawn around the pill, not around the row.
    val ownInteractionSource = remember { MutableInteractionSource() }
    val pillInteractionSource = interactionSource ?: ownInteractionSource
    val pillShape = if (selected) TacitShapes.selectedPill else TacitShapes.circle
    @OptIn(TrixnityMessengerPrivateApi::class)
    val avatarBitmap = remember(avatarImage) { avatarImage?.decodeToImageBitmapOrNull() }

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
                .background(if (selected) MaterialTheme.tacit.onAccentFixed else Color.Transparent)
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
                            selected -> MaterialTheme.tacit.accent(TacitAlpha.accentFill)
                            hovered -> MaterialTheme.tacit.surfaceAlt
                            else -> MaterialTheme.tacit.surface
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) MaterialTheme.tacit.accent.copy(alpha = TacitAlpha.borderEmphasis) else MaterialTheme.tacit.border.copy(alpha = 0.75f),
                        shape = pillShape,
                    )
                    .hoverable(hoverSource)
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(
                                interactionSource = pillInteractionSource,
                                indication = LocalIndication.current,
                                onClickLabel = label,
                                role = Role.Button,
                                onClick = onClick,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .tacitInteractive(interactionSource = pillInteractionSource, shape = pillShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isTacitHome) {
                    Image(
                        bitmap = imageResource(Res.drawable.tacit),
                        contentDescription = i18n.tacitAppName(),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(if (selected) TacitShapes.control else TacitShapes.circle),
                    )
                } else if (avatarBitmap != null) {

                    Image(
                        bitmap = avatarBitmap,
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
                        color = MaterialTheme.tacit.onAccentFixed,
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
                            .background(MaterialTheme.tacit.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = i18n.tacitDirectMessagesDescription(),
                            tint = MaterialTheme.tacit.text,
                            modifier = Modifier.size(9.dp),
                        )
                    }
                }
            }
            if (showDragAffordance && hovered) {
                // Static hint that the pill can be dragged; GuildDropHint only shows up once a drag
                // is already running.
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = null,
                    tint = MaterialTheme.tacit.textMuted,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(12.dp),
                )
            }
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 1.dp, y = -unreadBadgeOverhang)
                        .clip(CircleShape)
                        .background(MaterialTheme.tacit.accent)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        color = MaterialTheme.tacit.onAccent(MaterialTheme.tacit.accent),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
