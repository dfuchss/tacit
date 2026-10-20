package org.fuchss.matrix.tacit.views.room.timeline

import org.fuchss.matrix.tacit.ui.TacitAlpha
import org.fuchss.matrix.tacit.ui.TacitSizes
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
import androidx.compose.runtime.compositionLocalOf
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
import de.connect2x.trixnity.messenger.compose.view.room.timeline.HorizontalDividerWithText
import de.connect2x.trixnity.messenger.compose.view.room.timeline.TimelineViewElement
import de.connect2x.trixnity.messenger.compose.view.room.timeline.UnreadMessagesIndicator
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.TimelineElementHolderSwitch
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.TimelineElementViewSelector
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.BaseTimelineElementHolderViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.RedactedTimelineElementViewModel
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
import org.fuchss.matrix.tacit.tacit
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import kotlin.time.Duration.Companion.seconds

/** Test tag of a collapsed/expandable system event group header (used by the screenshot tooling). */
internal const val TacitSystemEventGroupTestTag = "tacitSystemEventGroup"

/**
 * `true` around a timeline element that directly follows a system event (see
 * [TacitSystemEventGroups.followsSystemEvent]): TacitFlatMessageView then starts a new sender block
 * (avatar, regular spacing) instead of continuing the previous message's. Provided by TacitTimelineView.
 */
internal val LocalTacitFollowsSystemEvent = compositionLocalOf { false }

/** Minimum number of consecutive system events that collapse into one row. */
private const val MinGroupSize = 2

/**
 * A maximal run of consecutive system (state) events. [members] are chronological (oldest first);
 * the oldest one, [headKey], is visually on top and renders the group's summary row.
 *
 * [undecryptableHistoryInfo] is the "history before you joined is not decryptable" notice of the
 * oldest member that carries one (upstream draws it as a divider above the user's own first
 * invite/join in encrypted rooms). The group draws it above its summary row instead, so it stays
 * visible while collapsed (see [TacitSystemEventHolder]).
 */
internal class TacitSystemEventGroup(
    val members: List<TimelineElementHolderViewModel>,
    val undecryptableHistoryInfo: String? = null,
) {
    val headKey: String = members.first().key
    val memberKeys: Set<String> = members.mapTo(LinkedHashSet()) { it.key }
}

/** Groups by element key; also lists every system event that is rendered in Tacit's quieter style. */
internal class TacitSystemEventGroups(
    private val byKey: Map<String, TacitSystemEventGroup>,
    private val quietKeys: Set<String>,
    private val followerKeys: Set<String> = emptySet(),
) {
    operator fun get(key: String): TacitSystemEventGroup? = byKey[key]

    /** Whether [key] is a groupable system event (grouped or isolated); those use [TacitSystemEventHolder]. */
    fun isSystemEvent(key: String): Boolean = key in quietKeys

    /**
     * Whether [key] directly follows a system event (see [SystemEventRuns.followers]): such a message
     * starts a new sender block even if the system event was sent by the same user.
     */
    fun followsSystemEvent(key: String): Boolean = key in followerKeys

    companion object {
        val Empty = TacitSystemEventGroups(emptyMap(), emptySet())
    }
}

/** How an element takes part in grouping (see [rememberTacitSystemEventGroups]). */
internal enum class SystemEventRole {
    /** Renders nothing (unsupported or ignored event): neither joins nor breaks a run. */
    Transparent,

    /**
     * Anything that is not a state event (messages, redacted elements, ...): ends the current run.
     * A [SystemEventGroupingInfo.hidden] one (redacted, which Tacit does not render) is otherwise
     * invisible: it does not hide the system event before it from the next message.
     */
    Breaking,

    /** A state event; groupable unless [SystemEventGroupingInfo.standalone]. */
    Groupable,
}

/**
 * Per-element state that is only known asynchronously and affects grouping: the element's [role]
 * (elements load, get redacted, ...); [standalone] system events never join a group (a room upgrade
 * is too important to fold away; the "rejoin" prompt carries extra UI with an action), and the
 * unread marker drawn below an event ends a run right after it ([breakAfter]). [undecryptableHistoryInfo] is the member event's "history is
 * not decryptable" notice; it does not keep the event out of a group (the group shows it instead).
 * A [hidden] element (redacted: Tacit does not render it) still ends a run, but is skipped when
 * deciding whether a message follows a system event.
 */
internal data class SystemEventGroupingInfo(
    val role: SystemEventRole,
    val standalone: Boolean = false,
    val breakAfter: Boolean = false,
    val undecryptableHistoryInfo: String? = null,
    val hidden: Boolean = false,
) {
    companion object {
        val Transparent = SystemEventGroupingInfo(SystemEventRole.Transparent)
        val Breaking = SystemEventGroupingInfo(SystemEventRole.Breaking)

        /** A redacted element: Tacit hides it, but it still separates the system events around it. */
        val Hidden = SystemEventGroupingInfo(SystemEventRole.Breaking, hidden = true)

        /** A state event that never joins a group (room upgrade). */
        val StandaloneState = SystemEventGroupingInfo(SystemEventRole.Groupable, standalone = true)
    }
}

private fun TimelineElementViewModel<*>?.isState(): Boolean = this is TimelineElementViewModel.State<*>

@OptIn(ExperimentalCoroutinesApi::class)
private fun BaseTimelineElementHolderViewModel.groupingInfo(): Flow<SystemEventGroupingInfo> =
    element.flatMapLatest { element ->
        when {
            element is TimelineElementViewModel.Empty -> flowOf(SystemEventGroupingInfo.Transparent)
            element is RedactedTimelineElementViewModel -> flowOf(SystemEventGroupingInfo.Hidden)
            this !is TimelineElementHolderViewModel || !element.isState() -> flowOf(SystemEventGroupingInfo.Breaking)
            element is TombstoneStateTimelineElementViewModel -> flowOf(SystemEventGroupingInfo.StandaloneState)
            element is MemberStateTimelineElementViewModel ->
                combine(showUnreadMarker, element.showRejoinRoomInfo, element.undecryptableHistoryInfo) { unread, rejoin, undecryptable ->
                    SystemEventGroupingInfo(
                        role = SystemEventRole.Groupable,
                        standalone = rejoin == true,
                        breakAfter = unread,
                        undecryptableHistoryInfo = undecryptable,
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
        element is RedactedTimelineElementViewModel -> SystemEventGroupingInfo.Hidden
        this !is TimelineElementHolderViewModel || !element.isState() -> SystemEventGroupingInfo.Breaking
        element is TombstoneStateTimelineElementViewModel -> SystemEventGroupingInfo.StandaloneState
        else -> SystemEventGroupingInfo(
            role = SystemEventRole.Groupable,
            standalone = element is MemberStateTimelineElementViewModel && element.showRejoinRoomInfo.value == true,
            breakAfter = showUnreadMarker.value,
            undecryptableHistoryInfo = (element as? MemberStateTimelineElementViewModel)?.undecryptableHistoryInfo?.value,
        )
    }
}

/**
 * Computes the system event groups of the timeline.
 *
 * Works on the *unfiltered* upstream elements (newest first) so that redacted elements -- which
 * Tacit hides from the rendered list -- still break a run. A run is broken by anything that is not a
 * groupable state event: messages, outbox elements, redacted or not yet loaded elements and
 * standalone state events (tombstones -- a room upgrade is too important to fold away -- and member
 * events with the rejoin prompt); by a date change (the date separator sits between two events
 * exactly when their dates differ); and right after an event that carries the unread marker. Empty
 * elements (unsupported or ignored events, which render nothing) are transparent: they neither join
 * nor break a run. The same pass marks the messages that directly follow a system event
 * ([TacitSystemEventGroups.followsSystemEvent]).
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
    // Oldest first, i.e. in visual top-to-bottom order.
    val holders = timelineViewElements.asReversed().mapNotNull { (it as? TimelineViewElement.Element)?.viewModel }
    val runs = groupSystemEventRuns(
        items = holders,
        infoOf = { holder ->
            if (holder !is TimelineElementHolderViewModel) SystemEventGroupingInfo.Breaking
            else groupingInfos[holder.key] ?: holder.currentGroupingInfo()
        },
        dateOf = { it.formattedDate },
    )
    val byKey = HashMap<String, TacitSystemEventGroup>()
    runs.groups.forEach { run ->
        // Only TimelineElementHolderViewModels are groupable (anything else is Breaking above).
        val group = TacitSystemEventGroup(
            run.members.map { it as TimelineElementHolderViewModel },
            run.undecryptableHistoryInfo,
        )
        group.members.forEach { byKey[it.key] = group }
    }
    return TacitSystemEventGroups(
        byKey = byKey,
        quietKeys = runs.quiet.mapTo(HashSet()) { it.key },
        followerKeys = runs.followers.mapTo(HashSet()) { it.key },
    )
}

/** Result of [groupSystemEventRuns]. */
internal class SystemEventRuns<T>(
    /** The groups (runs of at least [MinGroupSize] events), oldest first. */
    val groups: List<SystemEventRun<T>>,
    /** Every event rendered in Tacit's quieter system event style (grouped or isolated). */
    val quiet: List<T>,
    /**
     * Every non-state element (message, ...) whose older neighbour -- skipping transparent and hidden
     * (redacted) ones -- is
     * a state event, grouped or not. Upstream's `isFirstInUserSequence` only compares senders, so a
     * message right after its sender's own join would otherwise continue a "sender run" that has no
     * visible message in it (no avatar, tight spacing).
     */
    val followers: List<T> = emptyList(),
)

/** One group: its [members] (oldest first) and the notice of the oldest member that has one. */
internal class SystemEventRun<T>(val members: List<T>, val undecryptableHistoryInfo: String?)

/**
 * The grouping decision of [rememberTacitSystemEventGroups] as a pure function over [items] in
 * chronological order (oldest first); see there for what breaks a run.
 *
 * A group carries the "history is not decryptable" notice of its oldest member that has one. An
 * isolated event with that notice is not quiet: it keeps upstream's rendering (divider above an
 * accent pill), exactly as before such events took part in grouping.
 */
internal fun <T> groupSystemEventRuns(
    items: List<T>,
    infoOf: (T) -> SystemEventGroupingInfo,
    dateOf: (T) -> String?,
): SystemEventRuns<T> {
    val groups = ArrayList<SystemEventRun<T>>()
    val quiet = ArrayList<T>()
    val run = ArrayList<Pair<T, SystemEventGroupingInfo>>()
    val followers = ArrayList<T>()
    var previousIsState = false
    fun closeRun() {
        if (run.size >= MinGroupSize) {
            groups += SystemEventRun(run.map { it.first }, run.firstNotNullOfOrNull { it.second.undecryptableHistoryInfo })
            run.mapTo(quiet) { it.first }
        } else {
            run.filter { it.second.undecryptableHistoryInfo == null }.mapTo(quiet) { it.first }
        }
        run.clear()
    }
    for (item in items) {
        val info = infoOf(item)
        when (info.role) {
            SystemEventRole.Transparent -> continue
            SystemEventRole.Breaking -> {
                closeRun()
                // A hidden element ends the run but is not seen: the next message still follows the
                // system event before it.
                if (info.hidden) continue
                if (previousIsState) followers += item
            }
            SystemEventRole.Groupable -> if (info.standalone) {
                closeRun()
            } else {
                if (run.isNotEmpty() && dateOf(run.last().first) != dateOf(item)) closeRun()
                run += item to info
                if (info.breakAfter) closeRun()
            }
        }
        previousIsState = info.role == SystemEventRole.Groupable
    }
    closeRun()
    return SystemEventRuns(groups, quiet, followers)
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
 * A group with a "history is not decryptable" notice draws it (upstream's divider, in its regular
 * accent colour) above the summary row, expanded or not. Grouped member events therefore skip
 * upstream's own per-event divider (see [QuietSystemEvent]), so the notice never shows twice and
 * does not jump between the header and the event when the group is toggled.
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
    modifier: Modifier = Modifier,
) {
    val showUnreadMarker by holder.showUnreadMarker.collectAsState()
    val showLoadingIndicatorBefore by holder.showLoadingIndicatorBefore.collectAsState()
    val showLoadingIndicatorAfter by holder.showLoadingIndicatorAfter.collectAsState()
    Column(modifier) {
        AnimatedVisibility(showLoadingIndicatorBefore) { LoadingSpinner() }
        if (group == null) {
            QuietSystemEvent(holder, index, grouped = false)
        } else {
            if (group.headKey == holder.key) {
                group.undecryptableHistoryInfo?.let { HorizontalDividerWithText(it) }
                SystemEventGroupHeader(group, expanded, onToggle)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = if (snapOpen) EnterTransition.None else expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                QuietSystemEvent(holder, index, grouped = true)
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
 *
 * Upstream's member event view draws the "history is not decryptable" divider above the event in
 * `createInTimeline`; its `createAsPreview` is the same minus that divider (checked against 4.10.3).
 * A [grouped] member event is rendered through the latter, as its group shows the notice already.
 */
@Composable
private fun QuietSystemEvent(holder: TimelineElementHolderViewModel, index: Int, grouped: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val tokens = MaterialTheme.tacit
    val quietScheme = remember(colorScheme, tokens) {
        colorScheme.copy(tertiary = tokens.surfaceAlt.copy(alpha = 0.7f), onTertiary = tokens.textMuted)
    }
    val element = holder.element.collectAsState().value
    MaterialTheme(colorScheme = quietScheme) {
        // The pill's tooltip only repeats its text.
        WithoutTooltips {
            if (grouped && element is MemberStateTimelineElementViewModel) {
                DI.get<TimelineElementViewSelector>().createAsPreview(holder, element, index)
            } else {
                TimelineElementHolderSwitch(holder, index)
            }
        }
    }
}

private val SenderLookupTimeout = 5.seconds

/** Sender names that are already resolved (without subscribing), distinct and in chronological order. */
private fun List<TimelineElementHolderViewModel>.currentSenderNames(): List<String> =
    mapNotNull { it.sender.value }.distinctBy { it.userId }.map { it.name }.distinct()

@Composable
private fun SystemEventGroupHeader(
    group: TacitSystemEventGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    val background by animateColorAsState(MaterialTheme.tacit.surfaceAlt.copy(alpha = if (isHovered || expanded) 0.85f else 0.45f))
    val border by animateColorAsState(
        when {
            isFocused -> MaterialTheme.tacit.accent.copy(alpha = TacitAlpha.focusRing)
            isHovered -> MaterialTheme.tacit.border
            else -> MaterialTheme.tacit.border.copy(alpha = 0.5f)
        }
    )
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f)

    Box(modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .testTag(TacitSystemEventGroupTestTag)
                .clip(TacitShapes.pill)
                .background(background, TacitShapes.pill)
                .border(TacitSizes.borderWidth, border, TacitShapes.pill)
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
                color = MaterialTheme.tacit.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp).rotate(chevronRotation),
                tint = MaterialTheme.tacit.textMuted,
            )
        }
    }
}
