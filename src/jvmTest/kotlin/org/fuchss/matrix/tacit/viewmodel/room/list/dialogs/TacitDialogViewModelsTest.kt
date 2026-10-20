package org.fuchss.matrix.tacit.viewmodel.room.list.dialogs

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.Membership
import de.connect2x.trixnity.messenger.util.BackHandler
import de.connect2x.trixnity.messenger.util.BackHandlerImpl
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContext
import de.connect2x.trixnity.messenger.viewmodel.ViewModelContextImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.fuchss.matrix.tacit.viewmodel.i18n.TacitI18n
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.fuchss.matrix.tacit.viewmodel.util.UserSearchState
import org.fuchss.matrix.tacit.viewmodel.util.testTacitI18n
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The dialog view models on a real view-model context and Koin, without an account to act with. */
@OptIn(ExperimentalCoroutinesApi::class)
class TacitDialogViewModelsTest {
    private val guild = GuildEntry(
        roomId = RoomId("!guild:example.org"),
        userId = UserId("@tacit:example.org"),
        displayName = "Guild",
        avatarUri = null,
        membership = Membership.JOIN,
    )

    private fun TestScope.viewModelContext(): ViewModelContext {
        val koin = koinApplication {
            modules(module {
                single<BackHandler> { BackHandlerImpl() }
                single<TacitI18n> { testTacitI18n() }
            })
        }.koin
        return ViewModelContextImpl(
            di = koin,
            componentContext = DefaultComponentContext(LifecycleRegistry().apply { resume() }),
            coroutineContext = backgroundScope.coroutineContext,
            name = "RoomList",
        )
    }

    @Test
    fun `creating a guild without an account fails in the dialog and keeps it open`() = runTest(UnconfinedTestDispatcher()) {
        var created: GuildEntry? = null
        var closed = false
        val viewModel = TacitCreateGuildViewModel(viewModelContext().childContext("createGuild"), { null }) { created = it }

        viewModel.createGuild("Guild", "", createDefaultChannel = true) { closed = true }

        assertEquals(
            "Could not create guild: No active Matrix account available for guild creation.",
            viewModel.error.value,
        )
        assertFalse(viewModel.isCreating.value)
        assertEquals(null, created)
        assertFalse(closed)
        viewModel.errorDismiss()
        assertEquals(null, viewModel.error.value)
    }

    @Test
    fun `an invalid user ID fails before any account is needed`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = TacitCreateDirectMessageViewModel(viewModelContext().childContext("dm"), { null }) {}

        viewModel.startDirectMessage("alice") {}

        assertEquals("Could not start direct message: No valid userId provided: 'alice'", viewModel.error.value)
    }

    @Test
    fun `submitting the same invalid user ID twice reports a new failure each time`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = TacitCreateDirectMessageViewModel(viewModelContext().childContext("dm"), { null }) {}

        viewModel.startDirectMessage("foo") {}
        val first = viewModel.failure.value
        viewModel.startDirectMessage("foo") {}
        val second = viewModel.failure.value

        assertEquals("Could not start direct message: No valid userId provided: 'foo'", second?.message)
        assertEquals(first?.message, second?.message)
        assertNotEquals(first, second)
    }

    @Test
    fun `guild dialogs report a missing guild account`() = runTest(UnconfinedTestDispatcher()) {
        val context = viewModelContext()
        val createChannel = TacitCreateChannelViewModel(context.childContext("channel"), { null }) {}
        val invite = TacitInviteToGuildViewModel(context.childContext("invite")) { null }

        createChannel.createChannel(guild, "general", "") {}
        invite.inviteUser(guild, "@alice:example.org", "") {}

        assertEquals("Could not create room: Could not resolve active guild context.", createChannel.error.value)
        assertTrue(invite.error.value.orEmpty().endsWith("Could not resolve active guild context."))
    }

    @Test
    fun `the user search stays idle without an account`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = TacitInviteToGuildViewModel(viewModelContext().childContext("invite")) { null }

        viewModel.searchUsers(guild, "alice")

        assertEquals(UserSearchState.Idle, viewModel.userSearch.state.value)
    }
}
