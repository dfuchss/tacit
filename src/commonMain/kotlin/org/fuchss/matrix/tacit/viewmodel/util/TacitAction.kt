package org.fuchss.matrix.tacit.viewmodel.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n

/**
 * One exclusive, fallible action of a view model, following upstream's dialog view models (`CreateNewGroupViewModel`):
 * [isCreating] guards against a second start while the action runs (`_isCreating.getAndUpdate { true }`), and a
 * failure ends up in [error], rendered through [TacitI18n.tacitErrorMessage].
 */
internal class TacitAction(private val scope: CoroutineScope, private val i18n: TacitI18n) : TacitErrorSource {
    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Runs [block] unless the action is already running (then returns `null`). Clears the previous error first; on
     * failure the error becomes [failureMessage] of the localised reason, on success [onSuccess] runs.
     */
    fun <T> launch(
        failureMessage: TacitI18n.(reason: String) -> String,
        onSuccess: (T) -> Unit = {},
        block: suspend () -> Result<T>,
    ): Job? {
        if (_isCreating.getAndUpdate { true }) return null
        _error.value = null
        return scope.launch {
            block().fold(
                onSuccess = onSuccess,
                onFailure = { throwable -> _error.value = i18n.failureMessage(i18n.tacitErrorMessage(throwable)) },
            )
        }.also { job -> job.invokeOnCompletion { _isCreating.value = false } }
    }

    override fun errorDismiss() {
        _error.value = null
    }
}

/** Something whose failures the room list also shows in its error banner. */
internal interface TacitErrorSource {
    val error: StateFlow<String?>

    fun errorDismiss()
}
