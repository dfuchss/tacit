package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Interaction state of one message row: pointer hover on the row and on its quick-action popup
 * (tracked through interaction sources, so hover is never written during composition) plus the
 * open/closed state of the menus that hang off the row.
 */
@Stable
internal class TacitMessageRowMenuState {
    /** Hover of the message card itself; attach with `Modifier.hoverable`. */
    val messageInteractionSource = MutableInteractionSource()

    /** Hover of the quick-action popup, which is a separate window and does not count as hover of the card. */
    val quickActionsInteractionSource = MutableInteractionSource()

    /** The small reaction bar opened from the quick actions. */
    var quickReactionsOpen by mutableStateOf(false)

    /** The full reaction picker. */
    var allReactionsOpen by mutableStateOf(false)

    /** The message action dropdown (reply, edit, copy, ...). */
    var actionMenuOpen by mutableStateOf(false)

    /**
     * The row is "active" while it is hovered (card or quick-action popup) or while one of its
     * menus is open. Reads the hover interaction sources, so call it from composition only.
     */
    @Composable
    fun rememberIsActive(): Boolean {
        val hoverMessage by messageInteractionSource.collectIsHoveredAsState()
        val hoverQuickActions by quickActionsInteractionSource.collectIsHoveredAsState()
        return hoverMessage || hoverQuickActions || actionMenuOpen || quickReactionsOpen || allReactionsOpen
    }

    fun openActionMenu() {
        actionMenuOpen = true
    }

    fun closeActionMenu() {
        actionMenuOpen = false
    }

    fun openQuickReactions() {
        quickReactionsOpen = true
        allReactionsOpen = false
    }

    /** Closes everything that a reply from the quick actions dismisses. */
    fun closeAllMenus() {
        quickReactionsOpen = false
        allReactionsOpen = false
        actionMenuOpen = false
    }
}

@Composable
internal fun rememberTacitMessageRowMenuState(): TacitMessageRowMenuState = remember { TacitMessageRowMenuState() }
