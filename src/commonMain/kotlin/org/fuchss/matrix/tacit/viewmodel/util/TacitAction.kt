package org.fuchss.matrix.tacit.viewmodel.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n

/**
 * One exclusive, fallible action of a view model, following upstream's dialog view models (`CreateNewGroupViewModel`):
 * [isCreating] guards against a second start while the action runs (`_isCreating.getAndUpdate { true }`), and a
 * failure ends up in [error] and [failure], rendered through [TacitI18n.tacitErrorMessage].
 */
internal class TacitAction(
    private val scope: CoroutineScope,
    private val i18n: TacitI18n,
    private val errors: TacitErrorState = TacitErrorState(),
) : TacitErrorSource by errors {
    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

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
        errors.clear()
        return scope.launch {
            block().fold(
                onSuccess = onSuccess,
                onFailure = { throwable -> errors.fail(i18n.failureMessage(i18n.tacitErrorMessage(throwable))) },
            )
        }.also { job -> job.invokeOnCompletion { _isCreating.value = false } }
    }
}

/**
 * One failed attempt of an action. [attempt] counts the failures of its source, so a second failure with the same
 * [message] is still a new value: a dialog keyed on it shows the error again after the user retried.
 */
internal data class TacitFailure(val message: String, val attempt: Long)

/** Something whose failures the room list also shows in its error banner. */
internal interface TacitErrorSource {
    /** The message of the last failure, null once cleared. */
    val error: StateFlow<String?>

    /** The last failure, a new value for every failed attempt (also when the message repeats), null once cleared. */
    val failure: StateFlow<TacitFailure?>

    fun errorDismiss()
}

/** The error state behind a [TacitErrorSource]: [fail] reports a failure, [clear] (or a dismiss) drops it. */
internal class TacitErrorState : TacitErrorSource {
    private val attempts = MutableStateFlow(0L)

    private val _failure = MutableStateFlow<TacitFailure?>(null)
    override val failure: StateFlow<TacitFailure?> = _failure.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    fun fail(message: String) {
        _failure.value = TacitFailure(message, attempts.updateAndGet { it + 1 })
        _error.value = message
    }

    fun clear() {
        _failure.value = null
        _error.value = null
    }

    override fun errorDismiss() = clear()
}
