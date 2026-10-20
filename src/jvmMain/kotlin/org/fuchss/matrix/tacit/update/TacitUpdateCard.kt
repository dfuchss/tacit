package org.fuchss.matrix.tacit.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

/**
 * A floating card for a staged update, meant to be overlaid at the top end of the client, below the room header, inside
 * `MessengerTheme`. [version] is `null` while no update is staged; [dismissed] hides the card ("Later").
 */
@Composable
fun TacitUpdateCard(
    i18n: TacitI18nView,
    version: String?,
    dismissed: Boolean,
    onRestart: () -> Unit,
    onWhatsNew: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the last version while animating out, when [version] is already null.
    var shownVersion by remember { mutableStateOf<String?>(null) }
    if (version != null) shownVersion = version

    AnimatedVisibility(
        visible = version != null && !dismissed,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 4 },
        exit = fadeOut() + slideOutVertically { it / 4 },
    ) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = 480.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.SystemUpdateAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(i18n.tacitUpdateAvailableTitle(), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = i18n.tacitUpdateReady(shownVersion.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onWhatsNew) { Text(i18n.tacitUpdateWhatsNew()) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onLater) { Text(i18n.tacitUpdateLater()) }
                    Button(onClick = onRestart) { Text(i18n.tacitUpdateRestart(), maxLines = 1, softWrap = false) }
                }
            }
        }
    }
}
