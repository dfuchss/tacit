package org.fuchss.matrix.tacit.viewmodel.util

import kotlinx.coroutines.flow.SharingStarted

/**
 * `WhileSubscribed` that keeps upstream subscriptions alive for a few seconds after the last collector leaves, so a
 * recomposition or a quick back-and-forth does not tear down and rebuild whole flow pipelines.
 */
internal val tacitWhileSubscribed: SharingStarted = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000)
