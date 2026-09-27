package org.fuchss.matrix.tacit.readmeshots

import com.arkivanov.decompose.DecomposeSettings
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.backend.DefaultBackend
import de.connect2x.lognity.config.CoreConfigExtension
import de.connect2x.lognity.config.SerializableConfig
import de.connect2x.lognity.config.extension.ConfigExtension
import de.connect2x.lognity.config.setDefaultConfig
import de.connect2x.trixnity.client.MatrixClient
import de.connect2x.trixnity.client.flattenValues
import de.connect2x.trixnity.client.room
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.room.CreateEventContent
import de.connect2x.trixnity.core.model.events.m.space.ChildEventContent
import de.connect2x.trixnity.core.model.events.m.room.NameEventContent
import de.connect2x.trixnity.core.model.events.m.room.PowerLevelsEventContent
import de.connect2x.trixnity.messenger.MatrixClients
import de.connect2x.trixnity.messenger.MatrixMessenger
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsBase
import de.connect2x.trixnity.messenger.MatrixMessengerSettingsHolder
import de.connect2x.trixnity.messenger.createRoot
import de.connect2x.trixnity.messenger.multi.MatrixMultiMessenger
import de.connect2x.trixnity.messenger.multi.create
import de.connect2x.trixnity.messenger.multi.singleModeMatrixMessenger
import de.connect2x.trixnity.messenger.secrets.GetKey
import de.connect2x.trixnity.messenger.secrets.SecretByteArrayKeyProvider
import de.connect2x.trixnity.messenger.update
import de.connect2x.trixnity.messenger.util.RootPath
import de.connect2x.trixnity.messenger.viewmodel.MainViewModel
import de.connect2x.trixnity.messenger.viewmodel.RootRouter
import de.connect2x.trixnity.messenger.viewmodel.RootViewModel
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountMethod
import de.connect2x.trixnity.messenger.viewmodel.connecting.AddMatrixAccountViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.RoomRouter
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.InputAreaViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineRouter
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListRouter
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import de.connect2x.trixnity.messenger.viewmodel.settings.AccountSetupRouter
import de.connect2x.trixnity.messenger.viewmodel.util.toFlow
import de.connect2x.trixnity.messenger.viewmodel.verification.SelfVerificationRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonObject
import okio.Path
import org.fuchss.matrix.tacit.tammyConfiguration
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListViewModel
import org.fuchss.matrix.tacit.viewmodel.room.list.entry.GuildEntry
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

/** Same id as `PLATFORM_SECRET_BYTE_ARRAY_KEY_PROVIDER_ID` in trixnity-messenger (internal there). */
private const val PLATFORM_SECRET_KEY_PROVIDER_ID = "de.connect2x.trixnity.messenger.secrets.platform"

/** Mirrors `Main.configureLogging()` of the desktop app, but keeps the log directory inside [messengerDir]. */
internal fun configureScreenshotLogging(messengerDir: Path) {
    Backend.set(DefaultBackend)
    SerializableConfig uses CoreConfigExtension
    SerializableConfig uses ConfigExtension { registerProvider("MESSENGER_DIR") { messengerDir.toString() } }
    checkNotNull(object {}.javaClass.getResourceAsStream("/lognity.json")).use { stream ->
        Backend.setDefaultConfig(stream.asSource().buffered())
    }
}

/**
 * The real Tacit desktop configuration ([tammyConfiguration]) with the minimum of overrides needed to run headless
 * and hermetic:
 *  - app data lives in a throwaway directory instead of `./app-data` / `~/Library/Application Support`,
 *  - the account database (the app's regular SQLite/Room store) lives there too, unencrypted,
 *  - the OS keychain / credential store key provider is replaced by a no-op, so the run never touches the
 *    user's macOS Keychain or Windows credential vault.
 * Everything else (views, view models, theme, i18n, DI wiring) is exactly what `Main.kt` boots.
 */
internal suspend fun createScreenshotMultiMessenger(rootDir: Path): MatrixMultiMessenger =
    MatrixMultiMessenger.create(Dispatchers.Default) {
        tammyConfiguration {
            modulesFactories += { module { single<RootPath> { RootPath(rootDir) } } }
            messengerConfiguration {
                databaseEncryptionEnabled = false
                modulesFactories += { screenshotMessengerOverridesModule() }
            }
        }
    }

private fun screenshotMessengerOverridesModule() = module {
    single<SecretByteArrayKeyProvider>(named(PLATFORM_SECRET_KEY_PROVIDER_ID)) {
        object : SecretByteArrayKeyProvider {
            override val id: String = PLATFORM_SECRET_KEY_PROVIDER_ID
            override val level: Int = 0
            override suspend fun get(extra: JsonObject?, getInputKey: GetKey?): GetKey? = null
            override suspend fun rotate(
                oldExtra: JsonObject?,
                getOldInputKey: GetKey?,
                getNewInputKey: GetKey?,
            ): SecretByteArrayKeyProvider.RotateResult =
                SecretByteArrayKeyProvider.RotateResult(getOldKey = null, getNewKey = null, newExtra = null)

            @Deprecated("for backwards compatibility")
            override suspend fun getLegacy(): ByteArray? = null
        }
    }
}

internal class ScreenshotMessenger(val multi: MatrixMultiMessenger, val messenger: MatrixMessenger, val root: RootViewModel) {
    companion object {
        suspend fun start(rootDir: Path): ScreenshotMessenger {
            // Headless: there is no AWT main thread and the routers are driven from background coroutines (exactly
            // like trixnity-messenger's own integration tests), so decompose's main-thread assertion is just noise.
            DecomposeSettings.update { it.copy(mainThreadCheckEnabled = false) }
            val multi = createScreenshotMultiMessenger(rootDir)
            val messenger = multi.singleModeMatrixMessenger().first()
            messenger.di.get<MatrixMessengerSettingsHolder>().update<MatrixMessengerSettingsBase> {
                it.copy(preferredLang = "en") // the README is English, regardless of the machine's locale
            }
            val root = messenger.createRoot(DefaultComponentContext(LifecycleRegistry().apply { resume() }))
            return ScreenshotMessenger(multi, messenger, root)
        }
    }

    suspend fun main(): MainViewModel = root.stack.waitFor(RootRouter.Wrapper.Main::class).viewModel

    suspend fun roomList(): RoomListViewModel =
        main().roomListRouterStack.waitFor(RoomListRouter.Wrapper.List::class).viewModel

    /** Port of trixnity-messenger's integration test login: drives the real login view models. */
    suspend fun login(serverUrl: String, username: String, password: String) {
        val addAccount = root.stack.waitFor(RootRouter.Wrapper.AddMatrixAccount::class).viewModel
        addAccount.serverUrl.update(serverUrl)
        val passwordMethod = addAccount.serverDiscoveryState
            .filterIsInstance<AddMatrixAccountViewModel.ServerDiscoveryState.Success>()
            .first()
            .addMatrixAccountMethods
            .filterIsInstance<AddMatrixAccountMethod.Password>()
            .first()
        addAccount.selectAddMatrixAccountMethod(passwordMethod)
        val passwordLogin = root.stack.waitFor(RootRouter.Wrapper.PasswordLogin::class).viewModel
        passwordLogin.username.update(username)
        passwordLogin.password.update(password)
        passwordLogin.canLogin.first { it }
        passwordLogin.tryLogin()

        val main = main()
        main.accountSetupRouterStack.waitFor(AccountSetupRouter.Wrapper.ShowAccountSetup::class).viewModel.closeAccountSetup()
        main.accountSetupRouterStack.waitFor(AccountSetupRouter.Wrapper.None::class)

        // A fresh account has no cross-signing keys yet; set them up like a user would, so no verification
        // reminders show up in the screenshots.
        val bootstrap = withTimeoutOrNull(20.seconds) {
            main.selfVerificationStack.waitFor(SelfVerificationRouter.Wrapper.CrossSigningBootstrap::class).viewModel
        }
        if (bootstrap != null) {
            bootstrap.startCrossSigningBootstrap()
            bootstrap.isBootstrapRunning.first { it }
            bootstrap.isBootstrapRunning.first { !it }
            bootstrap.recoveryKey.first { it != null }
            bootstrap.close()
            main.selfVerificationStack.waitFor(SelfVerificationRouter.Wrapper.None::class)
        }
    }

    suspend fun roomElementNamed(nameFragment: String): RoomListElementViewModel {
        val roomList = roomList()
        while (true) {
            for (element in roomList.elements.value) {
                val name = withTimeoutOrNull(1.seconds) { element.roomName.first { it != null } }
                if (name != null && name.contains(nameFragment, ignoreCase = true)) return element
            }
            delay(200)
        }
    }

    suspend fun roomElement(roomId: RoomId): RoomListElementViewModel {
        val roomList = roomList()
        return roomList.elements.first { list -> list.any { it.roomId == roomId } }.first { it.roomId == roomId }
    }

    /** Switches the room list between the DM home (`null`) and a guild, like clicking a pill in the guild rail. */
    suspend fun selectGuild(guild: GuildEntry?) = (roomList() as TacitRoomListViewModel).selectGuild(guild)

    suspend fun selectRoom(roomId: RoomId): TimelineViewModel {
        roomList().selectRoom(roomId)
        return timeline(roomId)
    }

    /** The timeline view model of [roomId] once the room router shows that room (the room view may be replaced). */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun timeline(roomId: RoomId): TimelineViewModel =
        main().roomRouterStack.toFlow()
            .flatMapLatest { roomStack ->
                (roomStack.active.instance as? RoomRouter.Wrapper.View)?.viewModel?.timelineStack?.toFlow() ?: emptyFlow()
            }
            .first { (it.active.configuration as? TimelineRouter.Config.View)?.roomId == roomId.full }
            .active.instance.let { (it as TimelineRouter.Wrapper.View).viewModel }

    fun matrixClient(): MatrixClient = messenger.di.get<MatrixClients>().value.values.first()

    /** Room ids of the space's children (from its `m.space.child` state). */
    suspend fun spaceChildren(spaceRoom: RoomId): List<RoomId> =
        matrixClient().room.getAllState(spaceRoom, ChildEventContent::class).flattenValues()
            .map { children -> children.mapNotNull { child -> runCatching { RoomId(child.stateKey) }.getOrNull() } }
            .first { it.isNotEmpty() }

    /** The first space this account is a member of (the guild created through the dialog). */
    suspend fun findSpaceRoom(): RoomId =
        matrixClient().room.getAll().flattenValues()
            .first { rooms -> rooms.any { it.createEventContent?.type == CreateEventContent.RoomType.Space } }
            .first { it.createEventContent?.type == CreateEventContent.RoomType.Space }
            .roomId

    suspend fun describeRoomName(roomId: RoomId): String {
        val client = matrixClient()
        val room = client.room.getById(roomId).first()
        val nameState = client.room.getState(roomId, NameEventContent::class).first()
        val powerLevels = client.room.getState(roomId, PowerLevelsEventContent::class).first()
        return "room.name=${room?.name} membership=${room?.membership} m.room.name state=${nameState?.content} " +
            "m.room.power_levels state present=${powerLevels != null}"
    }

    suspend fun describeAllRooms(): String {
        val lines = mutableListOf<String>()
        for (room in matrixClient().room.getAll().flattenValues().first()) {
            lines += "${room.roomId} type=${room.createEventContent?.type} " + describeRoomName(room.roomId)
        }
        return lines.joinToString("\n")
    }

    suspend fun resendRoomName(roomId: RoomId, name: String) {
        matrixClient().api.room.sendStateEvent(roomId, NameEventContent(name)).getOrThrow()
    }

    suspend fun CoroutineScope.sendMessage(inputArea: InputAreaViewModel, text: String) {
        val keepHot = launch { inputArea.isSendEnabled.collect {} }
        inputArea.textField.update(text)
        inputArea.isSendEnabled.first { it }
        inputArea.sendMessage()
        keepHot.cancel()
    }
}

@Suppress("UNCHECKED_CAST")
internal suspend fun <C : Any, W : Any, T : Any> Value<ChildStack<C, W>>.waitFor(clazz: KClass<T>): T =
    toFlow().first { clazz.isInstance(it.active.instance) }.active.instance as T
