package org.fuchss.matrix.tacit.viewmodel.util

import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.media
import de.connect2x.trixnity.utils.toByteArray
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.cancellation.CancellationException

private const val MAX_AVATAR_BYTES = 512L * 1024L

/**
 * Loads an avatar thumbnail. Nothing is cached here (the Matrix client's media store already does that),
 * so a failure yields `null` only for this call and is retried on the next emission.
 */
internal suspend fun MatrixClient.loadAvatarThumbnail(uri: String?, size: Long): ByteArray? {
    if (uri.isNullOrBlank()) return null
    return try {
        media.getThumbnail(uri = uri, width = size, height = size, maxSize = MAX_AVATAR_BYTES)
            .getOrNull()
            ?.toByteArray(maxSize = MAX_AVATAR_BYTES)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

internal const val DEFAULT_AVATAR_PARALLELISM = 8

/** Maps all elements with at most [parallelism] transforms running at once, preserving order. */
internal suspend fun <T, R> Iterable<T>.mapConcurrently(
    parallelism: Int = DEFAULT_AVATAR_PARALLELISM,
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    require(parallelism > 0) { "parallelism must be positive" }
    val semaphore = Semaphore(parallelism)
    map { element -> async { semaphore.withPermit { transform(element) } } }.awaitAll()
}
