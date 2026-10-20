package org.fuchss.matrix.tacit.views.room.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.fuchss.matrix.tacit.tacit
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

@Composable
internal fun GuildHero(
    i18n: TacitI18nView,
    selectedGuild: GuildEntry?,
    visibleRoomsCount: Int,
    modifier: Modifier = Modifier,
) {
    val title = selectedGuild?.displayName?.ifBlank { null } ?: selectedGuild?.roomId?.full ?: i18n.tacitAllDms()
    val subtitle = if (selectedGuild == null) {
        i18n.tacitRoomsCount(visibleRoomsCount)
    } else {
        i18n.tacitRoomsInGuildCount(visibleRoomsCount)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(MaterialTheme.tacit.surface, MaterialTheme.tacit.surfaceAlt, MaterialTheme.tacit.surface)
                )
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.tacit.text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.tacit.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
