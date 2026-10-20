package org.fuchss.matrix.tacit.views.room.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

/**
 * The member keys of the expanded groups after the groups changed: every group that still contains
 * one of the [expandedKeys] is expanded (in full). [memberKeysOf] returns the member keys of the group
 * an event key belongs to, or `null` when the event is not (or no longer) grouped.
 */
internal fun expandedMemberKeys(expandedKeys: Collection<String>, memberKeysOf: (String) -> Set<String>?): Set<String> =
    expandedKeys.mapNotNullTo(HashSet()) { memberKeysOf(it) }.flatMapTo(HashSet()) { it }

/**
 * Which system event groups (see [TacitSystemEventGroups]) are expanded, remembered while their
 * events stay loaded. Stored as the member keys of the expanded groups; a group is expanded when any
 * of its members is in there. Groups change as the timeline does, and the expansion follows the events
 * (see [normalize]):
 * - growing (older history loaded, a new event appended) keeps a group expanded;
 * - merging (the event between two groups is redacted) expands the merged group when either part was
 *   expanded;
 * - splitting (an event lands inside a group) keeps every part expanded.
 *
 * Also tracks the group a jump unfolds ([snapOpenKey]): that group opens without animation until the
 * jump has scrolled.
 */
@Stable
internal class SystemEventExpansionState {
    private val expandedKeys = mutableStateMapOf<String, Unit>()

    /** Key of the event a jump unfolded its group for; that group opens without animation. */
    var snapOpenKey by mutableStateOf<String?>(null)
        private set

    fun isExpanded(group: TacitSystemEventGroup): Boolean = group.memberKeys.any { it in expandedKeys }

    fun toggle(group: TacitSystemEventGroup) {
        if (isExpanded(group)) group.memberKeys.forEach { expandedKeys.remove(it) }
        else group.memberKeys.forEach { expandedKeys[it] = Unit }
    }

    /**
     * Normalises the expansion to exactly the members of the expanded [groups]: newcomers are added
     * (so a later split stays consistent) and keys of events that are no longer in an expanded group
     * are dropped (so the map does not grow, and an event that later joins a group again starts
     * collapsed).
     */
    fun normalize(groups: TacitSystemEventGroups) {
        val target = expandedMemberKeys(expandedKeys.keys) { groups[it]?.memberKeys }
        expandedKeys.keys.filter { it !in target }.forEach { expandedKeys.remove(it) }
        target.forEach { if (it !in expandedKeys) expandedKeys[it] = Unit }
    }

    /** Whether the element [elementKey] belongs to the group a jump currently snaps open. */
    fun isSnapOpen(elementKey: String, groups: TacitSystemEventGroups): Boolean =
        snapOpenKey?.let { key -> groups[elementKey]?.memberKeys?.contains(key) } == true

    /** Opens the collapsed [group] of the jump target [key] at once (no expand animation). */
    fun snapOpen(key: String, group: TacitSystemEventGroup) {
        snapOpenKey = key
        if (!isExpanded(group)) toggle(group)
    }

    /** The jump has scrolled: groups animate again. */
    fun endSnapOpen() {
        snapOpenKey = null
    }
}

/** An expansion state that follows the changes of [groups] (see [SystemEventExpansionState.normalize]). */
@Composable
internal fun rememberSystemEventExpansionState(groups: State<TacitSystemEventGroups>): SystemEventExpansionState {
    val state = remember { SystemEventExpansionState() }
    LaunchedEffect(state, groups) {
        snapshotFlow { groups.value }.collect { state.normalize(it) }
    }
    return state
}
