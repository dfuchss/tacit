package org.fuchss.matrix.tacit.views.settings

import org.fuchss.matrix.tacit.ui.TacitSizes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import de.connect2x.trixnity.messenger.compose.view.getOrNull
import de.connect2x.trixnity.messenger.compose.view.settings.*
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedListItemButton
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedListItemSwitch
import de.connect2x.trixnity.messenger.viewmodel.MainViewModel
import de.connect2x.trixnity.messenger.viewmodel.settings.AppInfoViewModel
import de.connect2x.trixnity.messenger.viewmodel.settings.UserSettingsViewModel
import org.fuchss.matrix.tacit.BuildConfig
import org.fuchss.matrix.tacit.update.TacitUpdateController
import org.fuchss.matrix.tacit.update.TacitUpdateState
import org.fuchss.matrix.tacit.viewmodel.settings.TacitMarkAllAsReadState
import org.fuchss.matrix.tacit.viewmodel.settings.TacitUserSettingsViewModel
import org.fuchss.matrix.tacit.views.TacitAttributionFooter
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView

class TacitAccountSetupWizardStepList(
    private val features: MatrixMessengerConfiguration.Features = MatrixMessengerConfiguration.Features(),
) : AccountSetupWizardStepList {
    override val steps =
        if (features.enableNewAccountWizard) {
            listOf(AccountSetupWizardStep.V2.SettingsStep, AccountSetupWizardStep.V2.DeviceVerificationStep)
        } else {
            listOf(
                AccountSetupWizardStep.ExplanationStep,
                AccountSetupWizardStep.PrivacySettingsStep,
                AccountSetupWizardStep.NotificationSettingsStep,
                AccountSetupWizardStep.VerificationStep,
                AccountSetupWizardStep.ConfirmationStep,
            )
        }
}

class TacitUserSettingsView : UserSettingsView {
    @Composable
    override fun create(userSettingsViewModel: UserSettingsViewModel, mainViewModel: MainViewModel) {
        val i18n = DI.get<TacitI18nView>()
        Box(Modifier.fillMaxSize()) {
            Column {
                Header(userSettingsViewModel::closeUserSettings, i18n.commonSettings().capitalize(Locale.current))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AccountsInfoButton(userSettingsViewModel)
                    NotificationsSettingsButton(userSettingsViewModel)
                    AppearanceSettingsButton(userSettingsViewModel)
                    PrivacySettingsButton(userSettingsViewModel)
                    DeviceSettingsButton(userSettingsViewModel)
                    if (userSettingsViewModel.canShowProfilesSettings) {
                        ProfilesSettingsButton(userSettingsViewModel)
                    }
                    (userSettingsViewModel as? TacitUserSettingsViewModel)?.let { DevelopmentSettings(it) }
                }
            }
        }
    }
}

@Composable
private fun DevelopmentSettings(viewModel: TacitUserSettingsViewModel) {
    val i18n = DI.get<TacitI18nView>()
    val state by viewModel.markAllAsReadState.collectAsState()
    val running = state is TacitMarkAllAsReadState.Running
    Text(
        text = i18n.tacitDevelopmentTitle(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
    ThemedListItemButton(
        leadingContent = { Icon(Icons.Default.DoneAll, null) },
        headlineContent = { Text(i18n.tacitMarkAllAsRead()) },
        supportingContent = {
            Text(
                when (val current = state) {
                    TacitMarkAllAsReadState.Idle -> i18n.tacitMarkAllAsReadDescription()
                    is TacitMarkAllAsReadState.Running ->
                        current.total?.let { i18n.tacitMarkAllAsReadProgress(current.processed, it) }
                            ?: i18n.tacitMarkAllAsReadChecking()

                    is TacitMarkAllAsReadState.Done -> listOfNotNull(
                        when {
                            current.failedRooms > 0 ->
                                i18n.tacitMarkAllAsReadPartiallyFailed(current.markedRooms, current.failedRooms)

                            current.markedRooms == 0 && current.pendingInvites == 0 ->
                                i18n.tacitMarkAllAsReadNothingToDo()

                            else -> i18n.tacitMarkAllAsReadDone(current.markedRooms)
                        },
                        current.pendingInvites.takeIf { it > 0 }?.let { i18n.tacitMarkAllAsReadPendingInvites(it) },
                    ).joinToString("\n")

                    TacitMarkAllAsReadState.Failed -> i18n.tacitMarkAllAsReadFailed()
                }
            )
        },
        trailingContent = if (running) {
            { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
        } else null,
        enabled = !running,
        onClick = viewModel::markAllAsRead,
        modifier = Modifier.heightIn(min = TacitSizes.settingsRowMinHeight),
    )
}

@Composable
private fun TacitUpdateSettings(i18n: TacitI18nView, controller: TacitUpdateController) {
    val state by controller.state.collectAsState()
    val autoCheck by controller.autoCheck.collectAsState()
    val lastError by controller.lastError.collectAsState()
    val uriHandler = LocalUriHandler.current

    ThemedListItemSwitch(
        style = MaterialTheme.components.settingsItem,
        headlineContent = { Text(i18n.tacitUpdateAutoCheckTitle()) },
        supportingContent = { Text(i18n.tacitUpdateAutoCheckDescription()) },
        selected = autoCheck,
        onChange = { controller.setAutoCheck(it) },
    )
    val busy = state == TacitUpdateState.Checking || state is TacitUpdateState.Downloading
    ThemedListItemButton(
        headlineContent = { Text(i18n.tacitUpdateCheckNow()) },
        supportingContent = {
            Text(
                when (val current = state) {
                    TacitUpdateState.Idle -> i18n.tacitUpdateCurrentVersion(BuildConfig.version)
                    TacitUpdateState.Checking -> i18n.tacitUpdateChecking()
                    TacitUpdateState.UpToDate -> i18n.tacitUpdateUpToDate()
                    is TacitUpdateState.Downloading ->
                        i18n.tacitUpdateDownloading(current.version, current.progress?.let { (it * 100).toInt() })

                    is TacitUpdateState.ReadyToRestart -> i18n.tacitUpdateReady(current.version)
                    is TacitUpdateState.ManualOnly -> i18n.tacitUpdateManualOnly(current.version)
                    is TacitUpdateState.Failed -> i18n.tacitUpdateFailed(current.message)
                }
            )
        },
        trailingContent = if (busy) {
            { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
        } else null,
        enabled = !busy,
        onClick = controller::checkNow,
        modifier = Modifier.heightIn(min = TacitSizes.settingsRowMinHeight),
    )
    when (state) {
        is TacitUpdateState.ReadyToRestart -> ThemedListItemButton(
            headlineContent = { Text(i18n.tacitUpdateRestartToUpdate()) },
            onClick = controller::restartToUpdate,
            modifier = Modifier.heightIn(min = TacitSizes.settingsRowMinHeight),
        )

        is TacitUpdateState.ManualOnly -> ThemedListItemButton(
            headlineContent = { Text(i18n.tacitUpdateOpenReleasePage()) },
            onClick = { uriHandler.openUri(controller.releaseUrl) },
            modifier = Modifier.heightIn(min = TacitSizes.settingsRowMinHeight),
        )

        else -> Unit
    }
    // A failed swap is reported on the next start; the staged update stays available for a retry.
    lastError?.let { error ->
        Text(
            text = i18n.tacitUpdateFailed(error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
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
                DI.getOrNull<TacitUpdateController>()?.let { TacitUpdateSettings(i18n, it) }
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
                    modifier = Modifier.heightIn(min = TacitSizes.settingsRowMinHeight),
                )
                ThemedListItemButton(
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.CallSplit, "") },
                    headlineContent = { Text(i18n.tacitUpstreamTammy()) },
                    onClick = { uriHandler.openUri("https://gitlab.com/connect2x/tammy") },
                    modifier = Modifier.heightIn(min = TacitSizes.settingsRowMinHeight),
                )
                ThemedListItemButton(
                    leadingContent = { Icon(Icons.Outlined.AccountTree, "") },
                    headlineContent = { Text(i18n.tacitBaseProjectTrixnity()) },
                    onClick = {
                        uriHandler.openUri("https://gitlab.com/connect2x/trixnity-messenger/trixnity-messenger")
                    },
                    modifier = Modifier.heightIn(min = TacitSizes.settingsRowMinHeight),
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
