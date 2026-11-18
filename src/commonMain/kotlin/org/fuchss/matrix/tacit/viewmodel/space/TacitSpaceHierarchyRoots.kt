package org.fuchss.matrix.tacit.viewmodel.space

import de.connect2x.trixnity.core.model.RoomId

/*
 * Copied from Trixnity Messenger v4.11.0, viewmodel/spaces/SpaceHierarchy.kt (internal there). Replace with an import
 * once Trixnity Messenger makes `spaceHierarchyRoots` public.
 */

internal data class TacitSpaceHierarchyRoots(val topLevel: Set<RoomId>, val loopMembers: Set<RoomId>)

/**
 * Finds roots among the spaces in [childrenBySpace.keys]. Every member of a loop is also made a root so it remains
 * accessible, while descendants outside the loop stay nested.
 */
internal fun spaceHierarchyRoots(childrenBySpace: Map<RoomId, Set<RoomId>>): TacitSpaceHierarchyRoots {
    val spaceIdsInLoops = findSpacesInLoops(childrenBySpace)
    val nestedSpaceIds = childrenBySpace.values.flatMapTo(mutableSetOf()) { it }
    return TacitSpaceHierarchyRoots(childrenBySpace.keys - nestedSpaceIds + spaceIdsInLoops, spaceIdsInLoops)
}

/**
 * Finds loops using Kosaraju's algorithm:
 * 1. Walk child links and record each space after exploring its children.
 * 2. Reverse the links and walk spaces in decreasing finish order. Each traversal collects one group of mutually
 *    reachable spaces. The order prevents it from crossing into another group.
 * 3. Keep groups with multiple spaces, and single spaces linked to themselves. Other groups are not part of a loop,
 *    even when they are reachable from one.
 *
 * Both traversals use explicit stacks so deep hierarchies do not overflow the call stack.
 */
private fun findSpacesInLoops(childrenBySpace: Map<RoomId, Set<RoomId>>): Set<RoomId> {
    val finishOrder = spacesInFinishOrder(childrenBySpace)
    val parentsBySpace = reverseSpaceLinks(childrenBySpace)
    val visited = mutableSetOf<RoomId>()
    val spacesInLoops = mutableSetOf<RoomId>()
    for (startSpace in finishOrder.asReversed()) {
        if (!visited.add(startSpace)) continue
        val mutuallyReachableSpaces = mutableListOf<RoomId>()
        val pendingSpaces = ArrayDeque<RoomId>()
        pendingSpaces.addLast(startSpace)
        while (pendingSpaces.isNotEmpty()) {
            val spaceId = pendingSpaces.removeLast()
            mutuallyReachableSpaces += spaceId
            for (parentId in parentsBySpace.getValue(spaceId)) {
                if (visited.add(parentId)) pendingSpaces.addLast(parentId)
            }
        }
        val isLoop = mutuallyReachableSpaces.size > 1 || startSpace in childrenBySpace.getValue(startSpace)
        if (isLoop) spacesInLoops += mutuallyReachableSpaces
    }
    return spacesInLoops
}

private fun reverseSpaceLinks(childrenBySpace: Map<RoomId, Set<RoomId>>): Map<RoomId, Set<RoomId>> {
    val parentsBySpace = childrenBySpace.keys.associateWith { mutableSetOf<RoomId>() }
    childrenBySpace.forEach { (parentId, childIds) -> childIds.forEach { parentsBySpace[it]?.add(parentId) } }
    return parentsBySpace
}

/**
 * Records each space after exploring its child links, for example C, B, A for A -> B -> C. A visited set handles loops,
 * and a stack of child iterators avoids recursion in deep hierarchies.
 */
private fun spacesInFinishOrder(childrenBySpace: Map<RoomId, Set<RoomId>>): List<RoomId> {
    val visited = mutableSetOf<RoomId>()
    val finishedSpaces = mutableListOf<RoomId>()
    for (space in childrenBySpace.keys) {
        if (!visited.add(space)) continue
        val traversalStack = ArrayDeque<Pair<RoomId, Iterator<RoomId>>>()
        traversalStack.addLast(space to childrenBySpace.getValue(space).iterator())
        while (traversalStack.isNotEmpty()) {
            val (spaceId, remainingChildren) = traversalStack.last()
            if (remainingChildren.hasNext()) {
                val child = remainingChildren.next()
                if (child in childrenBySpace && visited.add(child)) {
                    traversalStack.addLast(child to childrenBySpace.getValue(child).iterator())
                }
            } else {
                finishedSpaces += spaceId
                traversalStack.removeLast()
            }
        }
    }

    return finishedSpaces
}
