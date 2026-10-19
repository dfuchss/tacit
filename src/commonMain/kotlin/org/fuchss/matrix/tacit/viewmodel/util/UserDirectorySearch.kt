package org.fuchss.matrix.tacit.viewmodel.util

import de.connect2x.trixnity.client.MatrixClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n

internal data class UserDirectoryEntry(
    val userId: String,
    val displayName: String,
)

internal suspend fun MatrixClient.searchUserDirectory(
    query: String,
    limit: Long = 20L,
): Result<List<UserDirectoryEntry>> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return Result.success(emptyList())

    return api.user.searchUsers(
        searchTerm = trimmedQuery,
        acceptLanguage = "",
        limit = limit,
    ).map { response ->
        val myUserId = userId.full
        response.results
            .asSequence()
            .filterNot { it.userId.full == myUserId }
            .map { result ->
                val resolvedUserId = result.userId.full
                val resolvedDisplayName = result.displayName?.ifBlank { null } ?: resolvedUserId
                UserDirectoryEntry(
                    userId = resolvedUserId,
                    displayName = resolvedDisplayName,
                )
            }
            .distinctBy { it.userId }
            .take(8)
            .toList()
    }
}

/** What a dialog shows below its user ID field while the user types. */
internal sealed interface UserSearchState {
    data object Idle : UserSearchState
    data object Searching : UserSearchState
    data class Results(val users: List<UserDirectoryEntry>) : UserSearchState
    data class Failed(val message: String) : UserSearchState
}

internal const val USER_SEARCH_MIN_QUERY_LENGTH = 2
internal const val USER_SEARCH_DEBOUNCE_MS = 220L

/**
 * The states of one search for [query]: [UserSearchState.Idle] for short queries or without a [search], else after
 * a debounce [UserSearchState.Searching] and then the outcome. Meant for `flatMapLatest`, so that typing on cancels
 * the previous search; until the debounce has passed, the previous state stays visible.
 */
internal fun userSearchStates(
    query: String,
    search: (suspend (query: String) -> Result<List<UserDirectoryEntry>>)?,
    failureMessage: (Throwable) -> String,
): Flow<UserSearchState> = flow {
    val trimmedQuery = query.trim()
    if (search == null || trimmedQuery.length < USER_SEARCH_MIN_QUERY_LENGTH) {
        emit(UserSearchState.Idle)
        return@flow
    }
    delay(USER_SEARCH_DEBOUNCE_MS)
    emit(UserSearchState.Searching)
    emit(search(trimmedQuery).fold({ UserSearchState.Results(it) }, { UserSearchState.Failed(failureMessage(it)) }))
}

/**
 * Debounced user-directory search of one dialog (inviting to a guild, starting a DM). Failures are localised; without
 * an account to search with, the search stays idle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class UserDirectorySearch(scope: CoroutineScope, private val i18n: TacitI18n) {
    private data class Request(val query: String, val client: MatrixClient?)

    private val request = MutableStateFlow(Request("", null))

    val state: StateFlow<UserSearchState> = request
        .flatMapLatest { (query, client) ->
            userSearchStates(
                query = query,
                search = client?.let { { query -> it.searchUserDirectory(query) } },
                failureMessage = { i18n.tacitUserSearchFailed(i18n.tacitErrorMessage(it)) },
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, UserSearchState.Idle)

    /** Searches [query] with [client]; an empty query (or no client) resets the search. */
    fun search(query: String, client: MatrixClient?) {
        request.value = Request(query, client)
    }
}
