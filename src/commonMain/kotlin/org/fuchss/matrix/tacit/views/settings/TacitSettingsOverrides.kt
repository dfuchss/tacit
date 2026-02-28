package org.fuchss.matrix.tacit.views.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.MatrixMessengerConfiguration
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.common.Header
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.settings.*
import de.connect2x.trixnity.messenger.viewmodel.MainViewModel
import de.connect2x.trixnity.messenger.viewmodel.settings.AppInfoViewModel
import de.connect2x.trixnity.messenger.viewmodel.settings.UserSettingsViewModel
import org.fuchss.matrix.tacit.views.TacitAttributionFooter

class TacitAccountSetupWizardStepList : AccountSetupWizardStepList {
    override val steps = listOf(
        AccountSetupWizardStep.ExplanationStep,
        AccountSetupWizardStep.NotificationSettingsStep,
        AccountSetupWizardStep.VerificationStep,
        AccountSetupWizardStep.ConfirmationStep,
    )
}

class TacitUserSettingsView : UserSettingsView {
    @Composable
    override fun create(userSettingsViewModel: UserSettingsViewModel, mainViewModel: MainViewModel) {
        val i18n = DI.get<I18nView>()
        Box(Modifier.fillMaxSize()) {
            Column {
                Header(userSettingsViewModel::closeUserSettings, i18n.commonSettings().capitalize(Locale.current))
                Column {
                    AccountsInfoButton(userSettingsViewModel)
                    NotificationsSettingsButton(userSettingsViewModel)
                    AppearanceSettingsButton(userSettingsViewModel)
                    DeviceSettingsButton(userSettingsViewModel)
                    ProfilesSettingsButton(userSettingsViewModel)
                }
            }
        }
    }
}

class TacitAppInfoView : AppInfoView {
    @Composable
    override fun create(appInfoViewModel: AppInfoViewModel) {
        val i18n = DI.get<I18nView>()
        Box(Modifier.fillMaxSize()) {
            Column {
                Header(
                    appInfoViewModel::close,
                    i18n.accountAboutTheApp(DI.get<MatrixMessengerConfiguration>().appName)
                        .capitalize(Locale.current),
                )
                AppInfoVersion(appInfoViewModel)
                PlatformAppInfo()
            }
        }
    }
}

class TacitLegalFooterView : LegalFooterView {
    @Composable
    override fun create() {
        TacitAttributionFooter(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            textStyle = MaterialTheme.typography.labelMedium,
        )
    }
}
