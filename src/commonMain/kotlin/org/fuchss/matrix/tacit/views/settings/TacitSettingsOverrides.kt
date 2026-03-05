package org.fuchss.matrix.tacit.views.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.messenger.MatrixMessengerConfiguration
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.common.Header
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.settings.*
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedListItemButton
import de.connect2x.trixnity.messenger.viewmodel.MainViewModel
import de.connect2x.trixnity.messenger.viewmodel.settings.AppInfoViewModel
import de.connect2x.trixnity.messenger.viewmodel.settings.UserSettingsViewModel
import org.fuchss.matrix.tacit.views.TacitAttributionFooter
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

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
        val i18n = DI.get<TacitI18nView>()
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
        val i18n = DI.get<TacitI18nView>()
        val uriHandler = LocalUriHandler.current
        val showLicenses = appInfoViewModel.showLicenses.collectAsState().value
        Box(Modifier.fillMaxSize()) {
            Column {
                Header(
                    appInfoViewModel::close,
                    i18n.accountAboutTheApp(DI.get<MatrixMessengerConfiguration>().appName)
                        .capitalize(Locale.current),
                )
                AppInfoVersion(appInfoViewModel)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = i18n.tacitAboutTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = i18n.tacitAboutDescription(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ThemedListItemButton(
                    leadingContent = { Icon(Icons.Outlined.Code, "") },
                    headlineContent = { Text(i18n.tacitProjectRepository()) },
                    onClick = { uriHandler.openUri("https://gitlab.com/dfuchss/tacit") },
                    modifier = Modifier.heightIn(min = 72.dp),
                )
                ThemedListItemButton(
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.CallSplit, "") },
                    headlineContent = { Text(i18n.tacitUpstreamTammy()) },
                    onClick = { uriHandler.openUri("https://gitlab.com/connect2x/tammy") },
                    modifier = Modifier.heightIn(min = 72.dp),
                )
                ThemedListItemButton(
                    leadingContent = { Icon(Icons.Outlined.AccountTree, "") },
                    headlineContent = { Text(i18n.tacitBaseProjectTrixnity()) },
                    onClick = {
                        uriHandler.openUri("https://gitlab.com/connect2x/trixnity-messenger/trixnity-messenger")
                    },
                    modifier = Modifier.heightIn(min = 72.dp),
                )
                LicensesLink(appInfoViewModel)
                Spacer(Modifier.weight(1f))
                PlatformAppInfo()
            }
        }
        if (showLicenses) AppInfoLicenses(appInfoViewModel)
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
