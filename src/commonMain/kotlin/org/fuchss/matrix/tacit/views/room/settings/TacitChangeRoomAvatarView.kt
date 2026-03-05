package org.fuchss.matrix.tacit.views.room.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.common.FilePickerType.IMAGE_FILE
import de.connect2x.trixnity.messenger.compose.view.common.FilePickerType.PHOTO_CAPTURE
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.compose.view.files.LoadFileDialog
import de.connect2x.trixnity.messenger.compose.view.files.filterFilePickerOptionsByAvailability
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.room.settings.ChangeRoomAvatarView
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedIconButton
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import de.connect2x.trixnity.messenger.viewmodel.room.settings.ChangeRoomAvatarViewModel
import org.fuchss.matrix.tacit.viewmodel.room.settings.TacitChangeRoomAvatarViewModel
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

class TacitChangeRoomAvatarView : ChangeRoomAvatarView {
    @Composable
    override fun create(changeRoomAvatarViewModel: ChangeRoomAvatarViewModel) {
        val canChangeAvatar = changeRoomAvatarViewModel.canChangeRoomAvatar.collectAsState().value
        val openSelector = changeRoomAvatarViewModel.openImageSelector.collectAsState().value
        val avatar = changeRoomAvatarViewModel.avatar.collectAsState().value
        val initials = changeRoomAvatarViewModel.initials.collectAsState().value
        val tacitViewModel = changeRoomAvatarViewModel as? TacitChangeRoomAvatarViewModel
        val i18n = DI.get<TacitI18nView>()

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.Center)) {
                Box {
                    ThemedUserAvatar(
                        initials = initials,
                        image = avatar,
                        presence = null,
                        size = this@BoxWithConstraints.maxWidth.coerceAtMost(200.dp),
                    ) {
                        if (canChangeAvatar) {
                            Tooltip({ Text(i18n.profileAvatarChange()) }) {
                                Box(Modifier.padding(10.dp)) {
                                    ThemedIconButton(
                                        style = MaterialTheme.components.secondaryIconButton,
                                        onClick = { changeRoomAvatarViewModel.openImageSelector.value = true },
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, i18n.profileAvatarChange())
                                    }
                                }
                            }
                        }
                    }
                    if (canChangeAvatar && tacitViewModel != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Tooltip({ Text(i18n.tacitRemoveAvatar()) }) {
                                ThemedIconButton(
                                    style = MaterialTheme.components.secondaryIconButton,
                                    onClick = { tacitViewModel.removeRoomAvatar() },
                                    enabled = canChangeAvatar,
                                ) {
                                    Icon(Icons.Default.Delete, i18n.tacitRemoveAvatar())
                                }
                            }
                        }
                    }
                }
            }
        }

        if (openSelector) {
            LoadFileDialog(
                filterFilePickerOptionsByAvailability(IMAGE_FILE, PHOTO_CAPTURE),
                onFileSelect = changeRoomAvatarViewModel::openAvatarCutter,
                onCloseLoadFileDialog = { changeRoomAvatarViewModel.openImageSelector.value = false },
            )
        }
    }
}
