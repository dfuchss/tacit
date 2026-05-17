package org.fuchss.matrix.tacit.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.messenger.compose.view.DI
import de.connect2x.trixnity.messenger.compose.view.common.Tooltip
import de.connect2x.trixnity.messenger.compose.view.common.modifier.expandable
import de.connect2x.trixnity.messenger.compose.view.get
import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.roomlist.header.*
import de.connect2x.trixnity.messenger.compose.view.theme.components
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedButton
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedDropdownMenu
import de.connect2x.trixnity.messenger.compose.view.theme.components.ThemedUserAvatar
import de.connect2x.trixnity.messenger.viewmodel.AccountInfo
import de.connect2x.trixnity.messenger.viewmodel.roomlist.AccountViewModel
import kotlinx.coroutines.flow.map

class TacitAccountAvatarView : AccountAvatarView {
    @Composable
    override fun RowScope.create(accountViewModel: AccountViewModel) {
        val activeAccount = accountViewModel.activeAccount.collectAsState().value
        if (activeAccount != null) {
            ActiveAccountDataWithoutBadge(activeAccount, accountViewModel)
        } else {
            NoAccountActiveAccountData(accountViewModel)
        }
    }
}

@Composable
private fun RowScope.ActiveAccountDataWithoutBadge(
    activeAccount: UserId,
    accountViewModel: AccountViewModel
) {
    val i18n = DI.get<I18nView>()
    val accounts = accountViewModel.accounts.collectAsState().value
    val accountSelectionOpen = remember { mutableStateOf(false) }
    val isSingleAccount = accountViewModel.isSingleAccount.collectAsState().value

    val activeAccountInfo = remember(accounts, activeAccount) {
        accountViewModel.accounts.map { accountInfos -> accountInfos.find { it.userId == activeAccount } }
    }.collectAsState(null).value

    val globalNotificationCount = accountViewModel.globalNotificationCount.collectAsState().value
    val accountNotificationCounts = accountViewModel.accountNotificationCounts.collectAsState().value

    if (activeAccountInfo != null) {
        Box(Modifier.weight(1.0f, false).fillMaxWidth()) {
            ThemedButton(
                style = MaterialTheme.components.accountSelector,
                onClick = {
                    if (isSingleAccount) accountViewModel.openUserAccounts()
                    else accountSelectionOpen.value = accountSelectionOpen.value.not()
                },
                modifier = Modifier.expandable(accountSelectionOpen),
            ) {
                AvatarAreaWithoutBadge(activeAccountInfo)
                if (isSingleAccount.not()) {
                    ThemedDropdownMenu(
                        expanded = accountSelectionOpen.value,
                        onDismissRequest = { accountSelectionOpen.value = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.background),
                    ) {
                        SelectAccountHeader(i18n.accountChangeAccount())
                        AllAccountsMenuItem(
                            selectAction = {
                                accountViewModel.selectActiveAccount(null)
                                accountSelectionOpen.value = false
                            },
                            iconOverlay = {
                                if (globalNotificationCount == null) return@AllAccountsMenuItem
                                AccountNotificationCount(globalNotificationCount)
                            }
                        )
                        accounts
                            .filterNot { account -> account.userId == activeAccount }
                            .forEach { account ->
                                AccountMenuItem(
                                    accountInfo = account,
                                    selectAction = { userId ->
                                        accountViewModel.selectActiveAccount(userId)
                                        accountSelectionOpen.value = false
                                    },
                                    iconOverlay = {
                                        AccountNotificationCount(
                                            accountNotificationCounts[account.userId] ?: return@AccountMenuItem
                                        )
                                    }
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarAreaWithoutBadge(accountInfo: AccountInfo) {
    val i18n = DI.get<I18nView>()
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                text =
                    AnnotatedString("${i18n.commonAccount()}: ${accountInfo.displayName}, ${accountInfo.userId.full}")
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemedUserAvatar(accountInfo.initials, accountInfo.avatar)
        Spacer(Modifier.size(10.dp))
        Column {
            Tooltip({ Text(accountInfo.displayName) }) {
                Text(
                    accountInfo.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Tooltip({ Text(accountInfo.userId.full) }) {
                Text(
                    accountInfo.userId.full,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}


