package org.fuchss.matrix.tacit.viewmodel.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Launches [block] only if [state] can be atomically moved from [idle] to [busy], so a fast double click cannot start
 * the same action twice. [state] returns to [idle] when the job completes for any reason (success, failure,
 * cancellation, or cancellation before it even started). Returns `null` if the action was already running.
 */
internal fun <T> CoroutineScope.launchExclusive(
    state: MutableStateFlow<T>,
    idle: T,
    busy: T,
    block: suspend CoroutineScope.() -> Unit,
): Job? {
    if (!state.compareAndSet(idle, busy)) return null
    val job = try {
        launch(block = block)
    } catch (throwable: Throwable) {
        state.value = idle
        throw throwable
    }
    job.invokeOnCompletion { state.value = idle }
    return job
}

internal fun CoroutineScope.launchExclusive(
    inProgress: MutableStateFlow<Boolean>,
    block: suspend CoroutineScope.() -> Unit,
): Job? = launchExclusive(inProgress, idle = false, busy = true, block = block)
