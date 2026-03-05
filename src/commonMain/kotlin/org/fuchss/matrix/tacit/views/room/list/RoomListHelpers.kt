package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.ui.graphics.Color
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import org.fuchss.matrix.tacit.accentColor

internal fun dmInitials(name: String): String {
    val parts = name.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

internal fun dmPresenceVisible(presence: Presence?): Boolean = presence == Presence.ONLINE

internal fun dmPresenceColor(presence: Presence?): Color = when (presence) {
    Presence.ONLINE -> accentColor
    else -> Color.Transparent
}

internal fun dmPresenceLabel(presence: Presence?, i18n: I18nView): String? = when (presence) {
    Presence.ONLINE -> i18n.presenceOnline()
    else -> null
}
