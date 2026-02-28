package org.fuchss.matrix.tacit

import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountMethod
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountViewModel
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

object PasswordOnlyAddMatrixAccountViewModelFactory : AddMatrixAccountViewModelFactory {
    override fun create(
        viewModelContext: ViewModelContext,
        onAddMatrixAccountMethod: (AddMatrixAccountMethod) -> Unit,
        onCancel: () -> Unit,
    ): AddMatrixAccountViewModel {
        val delegate = AddMatrixAccountViewModelFactory.create(
            viewModelContext = viewModelContext,
            onAddMatrixAccountMethod = onAddMatrixAccountMethod,
            onCancel = onCancel,
        )
        return PasswordOnlyAddMatrixAccountViewModel(delegate, viewModelContext)
    }
}

private class PasswordOnlyAddMatrixAccountViewModel(
    private val delegate: AddMatrixAccountViewModel,
    viewModelContext: ViewModelContext,
) : AddMatrixAccountViewModel, ViewModelContext by viewModelContext {
    override val isFirstMatrixClient: StateFlow<Boolean?> = delegate.isFirstMatrixClient
    override val serverUrl = delegate.serverUrl
    override val hasOtherAccountsOrProfiles: StateFlow<Boolean> = delegate.hasOtherAccountsOrProfiles
    override val isMultiProfile: StateFlow<Boolean> = delegate.isMultiProfile

    override val serverDiscoveryState: StateFlow<AddMatrixAccountViewModel.ServerDiscoveryState> =
        delegate.serverDiscoveryState.map { state ->
            when (state) {
                is AddMatrixAccountViewModel.ServerDiscoveryState.Success -> {
                    val passwordMethods = state.addMatrixAccountMethods
                        .filterIsInstance<AddMatrixAccountMethod.Password>()
                        .toSet()
                    if (passwordMethods.isNotEmpty()) {
                        AddMatrixAccountViewModel.ServerDiscoveryState.Success(passwordMethods)
                    } else {
                        AddMatrixAccountViewModel.ServerDiscoveryState.Failure(
                            "Password login is not available on this homeserver."
                        )
                    }
                }

                else -> state
            }
        }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(), AddMatrixAccountViewModel.ServerDiscoveryState.None)

    override fun selectAddMatrixAccountMethod(addMatrixAccountMethod: AddMatrixAccountMethod) {
        if (addMatrixAccountMethod is AddMatrixAccountMethod.Password) {
            delegate.selectAddMatrixAccountMethod(addMatrixAccountMethod)
        }
    }

    override fun cancel() {
        delegate.cancel()
    }
}
