package org.fuchss.matrix.tacit.viewmodel.settings

import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.settings.UserSettingsViewModel
import de.connect2x.trixnity.messenger.viewmodel.settings.UserSettingsViewModelFactory
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.get

internal interface TacitUserSettingsViewModel : UserSettingsViewModel {
    val markAllAsReadState: StateFlow<TacitMarkAllAsReadState>
    fun markAllAsRead()
}

internal object TacitUserSettingsViewModelFactory : UserSettingsViewModelFactory {
    override fun create(
        viewModelContext: ViewModelContext,
        onCloseUserSettings: () -> Unit,
        onShowDeviceSettings: () -> Unit,
        onShowAccounts: () -> Unit,
        onShowProfilesSettings: () -> Unit,
        onShowNotificationsSettings: () -> Unit,
        onShowPrivacySettings: () -> Unit,
        onShowAppearanceSettings: () -> Unit,
    ): UserSettingsViewModel {
        val delegate = UserSettingsViewModelFactory.create(
            viewModelContext = viewModelContext,
            onCloseUserSettings = onCloseUserSettings,
            onShowDeviceSettings = onShowDeviceSettings,
            onShowAccounts = onShowAccounts,
            onShowProfilesSettings = onShowProfilesSettings,
            onShowNotificationsSettings = onShowNotificationsSettings,
            onShowPrivacySettings = onShowPrivacySettings,
            onShowAppearanceSettings = onShowAppearanceSettings,
        )
        return TacitUserSettingsViewModelImpl(delegate, viewModelContext)
    }
}

private class TacitUserSettingsViewModelImpl(
    private val delegate: UserSettingsViewModel,
    viewModelContext: ViewModelContext,
) : TacitUserSettingsViewModel, UserSettingsViewModel by delegate, ViewModelContext by viewModelContext {
    private val markAllAsReadService = get<TacitMarkAllAsRead>()

    override val markAllAsReadState: StateFlow<TacitMarkAllAsReadState> = markAllAsReadService.state

    override fun markAllAsRead() = markAllAsReadService.start()
}
