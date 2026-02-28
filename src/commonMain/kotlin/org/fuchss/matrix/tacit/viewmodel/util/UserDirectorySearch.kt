package org.fuchss.matrix.tacit.viewmodel.util

import de.connect2x.trixnity.client.MatrixClient

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
