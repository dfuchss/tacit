package org.fuchss.matrix.tacit.viewmodel.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UserDirectorySearchTest {
    private val alice = UserDirectoryEntry("@alice:example.org", "Alice")

    @Test
    fun `short queries and a missing account stay idle without searching`() = runTest {
        var searches = 0
        val search: suspend (String) -> Result<List<UserDirectoryEntry>> = { searches++; Result.success(listOf(alice)) }

        assertEquals(listOf(UserSearchState.Idle), userSearchStates(" a ", search) { "failed" }.toList())
        assertEquals(listOf(UserSearchState.Idle), userSearchStates("alice", null) { "failed" }.toList())
        assertEquals(0, searches)
    }

    @Test
    fun `a query is searched trimmed after the debounce`() = runTest {
        val queries = mutableListOf<String>()
        val search: suspend (String) -> Result<List<UserDirectoryEntry>> = { queries += it; Result.success(listOf(alice)) }

        val states = userSearchStates("  ali ", search) { "failed" }.toList()

        assertEquals(listOf(UserSearchState.Searching, UserSearchState.Results(listOf(alice))), states)
        assertEquals(listOf("ali"), queries)
        assertEquals(USER_SEARCH_DEBOUNCE_MS, currentTime)
    }

    @Test
    fun `a failed search reports the localised message`() = runTest {
        val i18n = testTacitI18n("de")
        val states = userSearchStates("alice", { Result.failure(IllegalStateException("boom")) }) {
            i18n.tacitUserSearchFailed(i18n.tacitErrorMessage(it))
        }.toList()

        assertEquals(listOf(UserSearchState.Searching, UserSearchState.Failed("Suche fehlgeschlagen: boom")), states)
    }

    /** Lets the background search pass its debounce; `advanceUntilIdle` skips the background scope. */
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun `picking a hit keeps the results instead of searching its user ID again`() = runTest {
        val queries = mutableListOf<String>()
        val search = UserDirectorySearch<String>(backgroundScope, testTacitI18n()) { _, query ->
            queries += query
            Result.success(listOf(alice))
        }

        search.search("ali", "account")
        settle()
        assertEquals(UserSearchState.Results(listOf(alice)), search.state.value)

        // The dialog's field now holds the picked user ID; its search must not flash "Searching" over the hits.
        search.search(alice.userId, "account")
        settle()
        assertEquals(UserSearchState.Results(listOf(alice)), search.state.value)
        assertEquals(listOf("ali"), queries)

        // A real new query (or another account) still searches.
        search.search("bob", "account")
        settle()
        search.search(alice.userId, "other account")
        settle()
        assertEquals(listOf("ali", "bob", alice.userId), queries)
    }
}
