package org.fuchss.matrix.tacit.viewmodel.room.list.dialogs

import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import kotlinx.coroutines.flow.StateFlow
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.util.TacitAction
import org.fuchss.matrix.tacit.viewmodel.util.TacitErrorSource
import org.fuchss.matrix.tacit.viewmodel.util.TacitFailure
import org.koin.core.component.get

/**
 * A room-list dialog's view model. The room list creates one per dialog on its own `childContext`, like upstream's
 * `RoomListViewModelImpl` does for its child view models. [isCreating] is true while the dialog's action runs and
 * [error] holds its last failure; the dialog stays open on failure and closes through the action's `onSuccess`.
 */
internal abstract class TacitDialogViewModel(
    viewModelContext: ViewModelContext,
) : ViewModelContext by viewModelContext, TacitErrorSource {
    protected val tacitI18n: TacitI18n = get()
    protected val action = TacitAction(coroutineScope, tacitI18n)

    val isCreating: StateFlow<Boolean> = action.isCreating
    override val error: StateFlow<String?> = action.error
    override val failure: StateFlow<TacitFailure?> = action.failure

    override fun errorDismiss() = action.errorDismiss()
}
