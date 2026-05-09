package org.fuchss.matrix.tacit.views.room.list.dialogs.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.*
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.room.list.dialogs.TacitDialogHeroCard

@Composable
internal fun CreateCategoryDialog(
    guildName: String,
    isCreating: Boolean,
    canCreateCategory: Boolean,
    onDismiss: () -> Unit,
    onCreateCategory: (name: String) -> Unit,
) {
    val i18n = DI.get<TacitI18nView>()
    var categoryName by remember { mutableStateOf("") }

    ThemedModalDialog(onDismissRequest = onDismiss) {
        ModalDialogHeader {
            Text(i18n.tacitCreateCategoryTitle())
        }
        ModalDialogContent {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TacitDialogHeroCard(title = i18n.tacitCreateCategoryInGuild(guildName))
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(i18n.tacitCategoryNameLabel()) },
                    maxLines = 1,
                )
                if (!canCreateCategory) {
                    Text(
                        i18n.tacitNoAccountForGuild(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        ModalDialogFooter {
            ThemedButton(
                style = MaterialTheme.components.commonButton,
                onClick = onDismiss,
                enabled = !isCreating,
            ) {
                Text(i18n.commonCancel())
            }
            ThemedButton(
                style = MaterialTheme.components.primaryButton,
                onClick = { onCreateCategory(categoryName) },
                enabled = !isCreating && canCreateCategory && categoryName.isNotBlank(),
            ) {
                Text(if (isCreating) i18n.tacitCreateInProgress() else i18n.commonCreate())
            }
        }
    }
}
