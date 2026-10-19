package org.fuchss.matrix.tacit.ui

import androidx.compose.ui.unit.dp

/** Dimensions that repeat across Tacit's composables or carry a specific meaning. */
internal object TacitSizes {
    /** Width of every hairline border and divider. */
    val borderWidth = 1.dp

    /** Fixed width of the guild rail. */
    val guildRailWidth = 84.dp

    /** Minimum height of a two-line settings list row. */
    val settingsRowMinHeight = 72.dp

    /** Avatar diameter in compact lists (channel rows, member lists). */
    val avatarCompact = 28.dp
}
