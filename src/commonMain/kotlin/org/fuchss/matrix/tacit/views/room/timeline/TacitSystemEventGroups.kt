package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.common.LoadingSpinner
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.timeline.TimelineViewElement
import de.connect2x.trixnity.messenger.compose.view.room.timeline.UnreadMessagesIndicator
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.TimelineElementHolderSwitch
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.TimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.state.MemberStateTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.state.TombstoneStateTimelineElementViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.fuchss.matrix.tacit.accentColor
import org.fuchss.matrix.tacit.tacitBorder
import org.fuchss.matrix.tacit.tacitSurfaceAlt
import org.fuchss.matrix.tacit.tacitTextMuted
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import kotlin.time.Duration.Companion.seconds

/** Test tag of a collapsed/expandable system event group header (used by the screenshot tooling). */
internal const val TacitSystemEventGroupTestTag = "tacitSystemEventGroup"

/** Minimum number of consecutive system events that collapse into one row. */
private const val MinGroupSize = 2

/**
 * A maximal run of consecutive system (state) events. [members] are chronological (oldest first);
 * the oldest one, [headKey], is visually on top and renders the group's summary row.
 */
internal class TacitSystemEventGroup(val members: List<TimelineElementHolderViewModel>) {
    val headKey: String = members.first().key
    val memberKeys: Set<String> = members.mapTo(LinkedHashSet()) { it.key }
}

/** Groups by element key; also lists every system event that is rendered in Tacit's quieter style. */
internal class TacitSystemEventGroups(
    private val byKey: Map<String, TacitSystemEventGroup>,
    private val quietKeys: Set<String>,
) {
    operator fun get(key: String): TacitSystemEventGroup? = byKey[key]

    /** Whether [key] is a groupable system event (grouped or isolated); those use [TacitSystemEventHolder]. */
    fun isSystemEvent(key: String): Boolean = key in quietKeys

    companion object {
        val Empty = TacitSystemEventGroups(emptyMap(), emptySet())
    }
}

/** How an element takes part in grouping (see [rememberTacitSystemEventGroups]). */
private enum class SystemEventRole {
    /** Renders nothing (unsupported or ignored event): neither joins nor breaks a run. */
    Transparent,

    /** Anything that is not a groupable state event: ends the current run. */
    Breaking,

    /** A groupable state event. */
    Groupable,
}

/**
 * Per-element state that is only known asynchronously and affects grouping: the element's [role]
 * (elements load, get redacted, ...); [standalone] system events never join a group (they carry
 * extra UI such as the "rejoin" prompt or the "history before you joined is not decryptable"
 * divider), and the unread marker drawn below an event ends a run right after it ([breakAfter]).
 */
private data class SystemEventGroupingInfo(
    val role: SystemEventRole,
    val standalone: Boolean = false,
    val breakAfter: Boolean = false,
) {
    companion object {
        val Transparent = SystemEventGroupingInfo(SystemEventRole.Transparent)
        val Breaking = SystemEventGroupingInfo(SystemEventRole.Breaking)
    }
}

private fun TimelineElementViewModel<*>?.isGroupableState(): Boolean =
    this is TimelineElementViewModel.State<*> && this !is TombstoneStateTimelineElementViewModel

@OptIn(ExperimentalCoroutinesApi::class)
private fun BaseTimelineElementHolderViewModel.groupingInfo(): Flow<SystemEventGroupingInfo> =
    element.flatMapLatest { element ->
        when {
            element is TimelineElementViewModel.Empty -> flowOf(SystemEventGroupingInfo.Transparent)
            this !is TimelineElementHolderViewModel || !element.isGroupableState() -> flowOf(SystemEventGroupingInfo.Breaking)
            element is MemberStateTimelineElementViewModel ->
                combine(showUnreadMarker, element.showRejoinRoomInfo, element.undecryptableHistoryInfo) { unread, rejoin, undecryptable ->
                    SystemEventGroupingInfo(
                        role = SystemEventRole.Groupable,
                        standalone = rejoin == true || undecryptable != null,
                        breakAfter = unread,
                    )
                }

            else -> showUnreadMarker.map { SystemEventGroupingInfo(SystemEventRole.Groupable, breakAfter = it) }
        }
    }.distinctUntilChanged()

/** Snapshot of [groupingInfo], used until the element's collector delivered its first value. */
private fun BaseTimelineElementHolderViewModel.currentGroupingInfo(): SystemEventGroupingInfo {
    val element = element.value
    return when {
        element is TimelineElementViewModel.Empty -> SystemEventGroupingInfo.Transparent
        this !is TimelineElementHolderViewModel || !element.isGroupableState() -> SystemEventGroupingInfo.Breaking
        else -> SystemEventGroupingInfo(
            role = SystemEventRole.Groupable,
            standalone = element is MemberStateTimelineElementViewModel &&
                (element.showRejoinRoomInfo.value == true || element.undecryptableHistoryInfo.value != null),
            breakAfter = showUnreadMarker.value,
        )
    }
}

/**
 * Computes the system event groups of the timeline.
 *
 * Works on the *unfiltered* upstream elements (newest first) so that redacted elements -- which
 * Tacit hides from the rendered list -- still break a run. A run is broken by anything that is not a
 * groupable state event: messages, outbox elements, redacted or not yet loaded elements, tombstones
 * (a room upgrade is too important to fold away) and standalone member events; by a date change
 * (the date separator sits between two events exactly when their dates differ); and right after an
 * event that carries the unread marker. Empty elements (unsupported or ignored events, which render
 * nothing) are transparent: they neither join nor break a run.
 *
 * Everything that decides this is reactive: every loaded element gets one collector of its
 * [groupingInfo] (element type and flags) for as long as it stays loaded. Collectors are started and
 * cancelled by diffing the element list, so a list change does not resubscribe the others.
 */
@Composable
internal fun rememberTacitSystemEventGroups(
    timelineViewElements: State<List<TimelineViewElement>>,
): State<TacitSystemEventGroups> {
    val groupingInfos = remember(timelineViewElements) { mutableStateMapOf<String, SystemEventGroupingInfo>() }
    LaunchedEffect(timelineViewElements, groupingInfos) {
        val collectors = HashMap<String, Pair<BaseTimelineElementHolderViewModel, Job>>()
        snapshotFlow { timelineViewElements.value }.collect { elements ->
            val holders = HashMap<String, BaseTimelineElementHolderViewModel>(elements.size)
            elements.forEach { if (it is TimelineViewElement.Element) holders[it.viewModel.key] = it.viewModel }
            val iterator = collectors.entries.iterator()
            while (iterator.hasNext()) {
                val (key, collector) = iterator.next()
                if (holders[key] !== collector.first) {
                    collector.second.cancel()
                    iterator.remove()
                    groupingInfos.remove(key)
                }
            }
            holders.forEach { (key, holder) ->
                if (key !in collectors) {
                    collectors[key] = holder to launch { holder.groupingInfo().collect { groupingInfos[key] = it } }
                }
            }
        }
    }
    return remember(timelineViewElements, groupingInfos) {
        derivedStateOf { computeGroups(timelineViewElements.value, groupingInfos) }
    }
}

private fun computeGroups(
    timelineViewElements: List<TimelineViewElement>,
    groupingInfos: Map<String, SystemEventGroupingInfo>,
): TacitSystemEventGroups {
    val byKey = HashMap<String, TacitSystemEventGroup>()
    val quietKeys = HashSet<String>()
    val run = ArrayList<TimelineElementHolderViewModel>()
    fun closeRun() {
        if (run.size >= MinGroupSize) {
            val group = TacitSystemEventGroup(run.toList())
            run.forEach { byKey[it.key] = group }
        }
        run.clear()
    }
    // Oldest first, i.e. in visual top-to-bottom order.
    for (index in timelineViewElements.indices.reversed()) {
        val holder = (timelineViewElements[index] as? TimelineViewElement.Element)?.viewModel ?: continue
        val info = groupingInfos[holder.key] ?: holder.currentGroupingInfo()
        when {
            info.role == SystemEventRole.Transparent -> Unit
            info.role == SystemEventRole.Breaking || holder !is TimelineElementHolderViewModel -> closeRun()
            info.standalone -> closeRun()
            else -> {
                if (run.isNotEmpty() && run.last().formattedDate != holder.formattedDate) closeRun()
                quietKeys += holder.key
                run += holder
                if (info.breakAfter) closeRun()
            }
        }
    }
    closeRun()
    return TacitSystemEventGroups(byKey, quietKeys)
}

/**
 * Timeline holder for a groupable system event (replaces upstream's `TimelineElementHolder` for
 * those). Like upstream it shows the loading spinners and the unread marker around the element, so
 * these keep working for events that are folded away. The event itself is rendered by its regular
 * upstream view, but in a quieter colour scheme (see [QuietSystemEvent]).
 *
 * - isolated event ([group] == null): the event.
 * - head of a group (oldest event): the summary row, then the event while expanded.
 * - other members: only the event while expanded, nothing (zero height) while collapsed.
 *
 * [snapOpen] skips the expand animation: a jump to an event inside a collapsed group opens it at
 * once, so the scroll that follows measures the final layout instead of a half-open group.
 */
@Composable
internal fun TacitSystemEventHolder(
    holder: TimelineElementHolderViewModel,
    index: Int,
    group: TacitSystemEventGroup?,
    expanded: Boolean,
    snapOpen: Boolean,
    onToggle: () -> Unit,
) {
    val showUnreadMarker by holder.showUnreadMarker.collectAsState()
    val showLoadingIndicatorBefore by holder.showLoadingIndicatorBefore.collectAsState()
    val showLoadingIndicatorAfter by holder.showLoadingIndicatorAfter.collectAsState()
    Column {
        AnimatedVisibility(showLoadingIndicatorBefore) { LoadingSpinner() }
        if (group == null) {
            QuietSystemEvent(holder, index)
        } else {
            if (group.headKey == holder.key) SystemEventGroupHeader(group, expanded, onToggle)
            AnimatedVisibility(
                visible = expanded,
                enter = if (snapOpen) EnterTransition.None else expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                QuietSystemEvent(holder, index)
            }
        }
        AnimatedVisibility(showUnreadMarker) { UnreadMessagesIndicator() }
        AnimatedVisibility(showLoadingIndicatorAfter) { LoadingSpinner() }
    }
}

/**
 * Upstream draws every state event as a pill in `colorScheme.tertiary` (Tacit's bright accent).
 * Re-theming just that slot around the element turns all of them -- whatever their type -- into a
 * muted pill without overriding each state view. The unread marker, which also uses `tertiary`, is
 * drawn outside of this scope and keeps its accent.
 */
@Composable
private fun QuietSystemEvent(holder: TimelineElementHolderViewModel, index: Int) {
    val colorScheme = MaterialTheme.colorScheme
    val quietScheme = remember(colorScheme) {
        colorScheme.copy(tertiary = tacitSurfaceAlt.copy(alpha = 0.7f), onTertiary = tacitTextMuted)
    }
    MaterialTheme(colorScheme = quietScheme) {
        TimelineElementHolderSwitch(holder, index)
    }
}

private val SenderLookupTimeout = 5.seconds

/** Sender names that are already resolved (without subscribing), distinct and in chronological order. */
private fun List<TimelineElementHolderViewModel>.currentSenderNames(): List<String> =
    mapNotNull { it.sender.value }.distinctBy { it.userId }.map { it.name }.distinct()

@Composable
private fun SystemEventGroupHeader(group: TacitSystemEventGroup, expanded: Boolean, onToggle: () -> Unit) {
    val i18n = DI.get<TacitI18nView>()
    // Distinct senders in chronological order. Names resolve asynchronously, but only the first two
    // and the number of distinct senders are shown: look the senders up one after the other (each a
    // short subscription) instead of keeping a sender subscription open for every member.
    val senderNames by produceState(group.members.currentSenderNames(), group.members) {
        val names = LinkedHashMap<UserId, String>()
        for (holder in group.members) {
            val sender = withTimeoutOrNull(SenderLookupTimeout) { holder.sender.filterNotNull().first() } ?: continue
            if (names.put(sender.userId, sender.name) == null) value = names.values.distinct()
        }
    }
    val summary = i18n.tacitSystemEventGroupSummary(group.members.size, senderNames)
    val actionLabel = if (expanded) i18n.tacitSystemEventGroupCollapse() else i18n.tacitSystemEventGroupExpand()

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val background by animateColorAsState(tacitSurfaceAlt.copy(alpha = if (isHovered || expanded) 0.85f else 0.45f))
    val border by animateColorAsState(
        when {
            isFocused -> accentColor.copy(alpha = 0.8f)
            isHovered -> tacitBorder
            else -> tacitBorder.copy(alpha = 0.5f)
        }
    )
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f)

    Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .testTag(TacitSystemEventGroupTestTag)
                .clip(TacitShapes.pill)
                .background(background, TacitShapes.pill)
                .border(1.dp, border, TacitShapes.pill)
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = actionLabel,
                    onClick = onToggle,
                )
                .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = tacitTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp).rotate(chevronRotation),
                tint = tacitTextMuted,
            )
        }
    }
}
