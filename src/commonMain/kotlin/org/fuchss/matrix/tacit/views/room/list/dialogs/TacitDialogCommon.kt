package org.fuchss.matrix.tacit.views.room.list.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import org.fuchss.matrix.tacit.*
import org.fuchss.matrix.tacit.ui.TacitCardSurface
import org.fuchss.matrix.tacit.ui.TacitShapes
import org.fuchss.matrix.tacit.viewmodel.util.UserDirectoryEntry
import org.fuchss.matrix.tacit.views.room.list.dmInitials

@Composable
internal fun TacitDialogHeroCard(
    eyebrow: String? = null,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(TacitShapes.section)
            .background(
                Brush.horizontalGradient(
                    listOf(tacitSurface, tacitSurfaceAlt, tacitSurface)
                )
            )
            .border(1.dp, tacitBorder.copy(alpha = 0.8f), TacitShapes.section)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            eyebrow?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = tacitTextMuted,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = tacitText,
            )
        }
    }
}

@Composable
internal fun TacitDialogSearchResultsContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(TacitShapes.card)
            .background(tacitSurface)
            .border(1.dp, tacitBorder, TacitShapes.card)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
internal fun TacitUserDirectoryRow(
    user: UserDirectoryEntry,
    selected: Boolean,
    selectedLabel: String,
    onClick: () -> Unit,
) {
    TacitCardSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(0.dp),
        shape = TacitShapes.compact,
        backgroundColor = tacitSurface,
        borderColor = if (selected) accentColor.copy(alpha = 0.65f) else tacitBorder.copy(alpha = 0.55f),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemedUserAvatar(
                initials = dmInitials(user.displayName),
                image = null,
                size = 24.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.userId,
                    style = MaterialTheme.typography.bodySmall,
                    color = tacitTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
