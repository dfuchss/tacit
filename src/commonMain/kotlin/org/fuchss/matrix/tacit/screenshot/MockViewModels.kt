package org.fuchss.matrix.tacit.screenshot

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import de.connect2x.trixnity.client.media.PlatformMedia
import de.connect2x.trixnity.core.MSC2448
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import de.connect2x.trixnity.core.model.events.m.Presence
import de.connect2x.trixnity.core.model.events.m.room.RoomMessageEventContent
import de.connect2x.trixnity.crypto.key.UserTrustLevel
import de.connect2x.trixnity.messenger.abi.TrixnityMessengerPrivateApi
import de.connect2x.trixnity.messenger.util.FileDescriptor
import de.connect2x.trixnity.messenger.util.FileTransferProgressElement
import de.connect2x.trixnity.messenger.util.html.HtmlNode
import de.connect2x.trixnity.messenger.viewmodel.*
import de.connect2x.trixnity.messenger.viewmodel.initialsync.InitialSyncRouter
import de.connect2x.trixnity.messenger.viewmodel.media.AudioRecorderViewModel
import de.connect2x.trixnity.messenger.viewmodel.media.MediaPlayerViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.RoomRouter
import de.connect2x.trixnity.messenger.viewmodel.room.RoomViewModel
import de.connect2x.trixnity.messenger.viewmodel.room.settings.ExtrasRouter
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.*
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.*
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.EventIdOrTransactionId.Companion.EventIdOrTransactionId
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.elements.message.RoomMessageTimelineElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.AccountViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListElementViewModel
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListRouter
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModel
import de.connect2x.trixnity.messenger.viewmodel.settings.AccountSetupRouter
import de.connect2x.trixnity.messenger.viewmodel.settings.AvatarCutterRouter
import de.connect2x.trixnity.messenger.viewmodel.sharing.SharingRouter
import de.connect2x.trixnity.messenger.viewmodel.util.ErrorType
import de.connect2x.trixnity.messenger.viewmodel.util.EventReaction
import de.connect2x.trixnity.messenger.viewmodel.util.EventReactions
import de.connect2x.trixnity.messenger.viewmodel.verification.SelfVerificationRouter
import de.connect2x.trixnity.messenger.viewmodel.verification.VerificationRouter
import de.connect2x.trixnity.utils.nextString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

val selectedRoom = RoomId("!selected")

class MockMainViewModel(
    locale: Locale,
    showRoom: MutableStateFlow<Boolean>,
) : MainViewModel {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val selectedRoomId: MutableStateFlow<RoomId?> = MutableStateFlow(selectedRoom)

    override val avatarCutterRouterStack: Value<ChildStack<AvatarCutterRouter.Config, AvatarCutterRouter.Wrapper>> =
        MutableValue(ChildStack(AvatarCutterRouter.Config.None, AvatarCutterRouter.Wrapper.None))
    override val selfVerificationStack: Value<ChildStack<SelfVerificationRouter.Config, SelfVerificationRouter.Wrapper>> =
        MutableValue(ChildStack(SelfVerificationRouter.Config.None, SelfVerificationRouter.Wrapper.None))
    override val deviceVerificationRouterStack: Value<ChildStack<VerificationRouter.Config, VerificationRouter.Wrapper>> =
        MutableValue(ChildStack(VerificationRouter.Config.None, VerificationRouter.Wrapper.None))
    override val initialSyncStack: Value<ChildStack<InitialSyncRouter.Config, InitialSyncRouter.Wrapper>> =
        MutableValue(ChildStack(InitialSyncRouter.Config.None, InitialSyncRouter.Wrapper.None))
    override val accountSetupRouterStack: Value<ChildStack<AccountSetupRouter.Config, AccountSetupRouter.Wrapper>> =
        MutableValue(ChildStack(AccountSetupRouter.Config.None, AccountSetupRouter.Wrapper.None))
    override val sharingStack: Value<ChildStack<SharingRouter.Config, SharingRouter.Wrapper>> =
        MutableValue(ChildStack(SharingRouter.Config.None, SharingRouter.Wrapper.None))

    val room: MutableValue<ChildStack<RoomRouter.Config, RoomRouter.Wrapper>> =
        MutableValue(ChildStack(RoomRouter.Config.None, RoomRouter.Wrapper.None))

    init {
        coroutineScope.launch {
            showRoom.collect {
                if (it) {
                    room.value = ChildStack(
                        RoomRouter.Config.View(UserId("tammy", "server"), selectedRoom.full),
                        RoomRouter.Wrapper.View(RoomViewModelMock(locale))
                    )
                } else {
                    ChildStack(RoomRouter.Config.None, RoomRouter.Wrapper.None)
                }
            }
        }
    }

    override val roomListRouterStack: Value<ChildStack<RoomListRouter.Config, RoomListRouter.Wrapper>> =
        MutableValue(
            ChildStack(
                RoomListRouter.Config.RoomList,
                RoomListRouter.Wrapper.List(RoomListViewModelMock(locale, room))
            )
        )

    override val roomRouterStack: Value<ChildStack<RoomRouter.Config, RoomRouter.Wrapper>> = room

    override fun closeDetailsAndShowList() {}
    override fun onOpenAvatarCutter(userId: UserId, file: FileDescriptor) {}
    override fun onOpenAvatarCutter(userId: UserId, selectedRoomId: RoomId, file: FileDescriptor) {}
    override fun onRoomSelected(userId: UserId, id: RoomId, via: Set<String>?) {}
    override fun openMention(userId: UserId, timelineElementMention: TimelineElementMention) {}
    override fun openSelfVerification(userId: UserId) {}
    override fun start() {}
}

class RoomListViewModelMock(
    private val locale: Locale,
    private val room: MutableValue<ChildStack<RoomRouter.Config, RoomRouter.Wrapper>>,
) : RoomListViewModel {
    override val accountViewModel: AccountViewModel = AccountViewModelMock()
    override val canCreateNewRoomWithAccount: StateFlow<Boolean> = MutableStateFlow(true)
    override val closeProfileNeeded: Boolean = false
    override val error: StateFlow<String?> = MutableStateFlow(null)
    override val errorType: StateFlow<ErrorType> = MutableStateFlow(ErrorType.JUST_DISMISS)
    override val initialSyncFinished: StateFlow<Boolean> = MutableStateFlow(true)
    override val syncStates: StateFlow<RoomListViewModel.UserSyncStates> =
        MutableStateFlow(RoomListViewModel.UserSyncStates(setOf(), setOf()))
    override val searchTerm: TextFieldViewModel = TextFieldViewModelImpl(100)
    override val selectedRoomId: StateFlow<RoomId?> = MutableStateFlow(selectedRoom)
    override val showSearch: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val searchResultsEmpty: StateFlow<Boolean> = MutableStateFlow(false)
    override val unverifiedAccounts: StateFlow<List<UserId>> = MutableStateFlow(emptyList())

    override val elements: StateFlow<List<RoomListElementViewModel>> = MutableStateFlow(
        listOf(
            RoomListElementViewModelMock(
                roomName = "Timmy",
                roomImage = null,
                inviterUserInfo = UserInfoElement(UserId("tammy", "server"), "Timmy", "T", null)
            ),
            RoomListElementViewModelMock(
                roomName = "Tommy",
                roomImage = PlatformResource.resource("user1.png"),
                time = "19:59",
                lastMessage = mapOf(
                    Locale.ENGLISH to "Great :-)",
                    Locale.GERMAN to "Super :-)",
                )[locale] ?: "???", unreadMessages = 1
            ),
            RoomListElementViewModelMock(
                roomName = mapOf(
                    Locale.ENGLISH to "Mommy",
                    Locale.GERMAN to "Mama",
                )[locale] ?: "???",
                roomImage = PlatformResource.resource("user2.png"),
                time = "17:44",
                lastMessage = mapOf(
                    Locale.ENGLISH to "Thank you honey.",
                    Locale.GERMAN to "Danke liebes.",
                )[locale] ?: "???"
            ),
            RoomListElementViewModelMock(
                roomName = "Bob",
                roomImage = PlatformResource.resource("user3.png"),
                time = "17:10",
                lastMessage = "🥰",
                unreadMessages = 3
            ),
            RoomListElementViewModelMock(
                roomName = "Frank",
                roomImage = PlatformResource.resource("user4.png"),
                time = "16:52",
                lastMessage = mapOf(
                    Locale.ENGLISH to "Then let's just try it out ☺️",
                    Locale.GERMAN to "Na dann einfach probieren ☺️",
                )[locale] ?: "???"
            ),
            RoomListElementViewModelMock(
                roomName = mapOf(
                    Locale.ENGLISH to "Board Games Night",
                    Locale.GERMAN to "Brettspielnacht",
                )[locale] ?: "???",
                roomImage = PlatformResource.resource("boardgame.png"),
                time = "16:44",
                lastMessage = mapOf(
                    Locale.ENGLISH to "You: Alright, see you next week at the latest!",
                    Locale.GERMAN to "Du: Alles klar, dann bis spätestens nächste Woche!",
                )[locale] ?: "???",
                isDirect = false,
            ),
            RoomListElementViewModelMock(
                roomName = mapOf(
                    Locale.ENGLISH to "Family",
                    Locale.GERMAN to "Familie",
                )[locale] ?: "???",
                roomImage = PlatformResource.resource("family.png"),
                time = "11:30",
                lastMessage = mapOf(
                    Locale.ENGLISH to "Michael: That was fun!",
                    Locale.GERMAN to "Michael: Das hat Spaß gemacht!",
                )[locale] ?: "???",
                isDirect = false
            ),
            RoomListElementViewModelMock(
                roomName = "Luna",
                roomImage = PlatformResource.resource("user5.png"),
                time = "11:12",
                lastMessage = "Sent an image."
            ),
            RoomListElementViewModelMock(
                roomName = mapOf(
                    Locale.ENGLISH to "Movie Maniacs",
                    Locale.GERMAN to "Filmfans",
                )[locale] ?: "???",
                roomImage = PlatformResource.resource("movie.png"),
                time = "11:11",
                lastMessage = mapOf(
                    Locale.ENGLISH to "Benedict: Best movie ever 👍",
                    Locale.GERMAN to "Benedict: Bester Film überhaupt 👍",
                )[locale] ?: "???",
                isDirect = false,
                isEncrypted = false,
                isPublic = true
            ),
            RoomListElementViewModelMock(
                roomName = "Lilly",
                roomImage = PlatformResource.resource("user6.png"),
                time = "07:16",
                lastMessage = mapOf(
                    Locale.ENGLISH to "Oh yeah. I understand...",
                    Locale.GERMAN to "Oh ja. Kann ich verstehen...",
                )[locale] ?: "???",
            ),
            RoomListElementViewModelMock(
                roomName = "Nina",
                roomImage = PlatformResource.resource("user7.png"),
                time = "12.06.2024",
                lastMessage = mapOf(
                    Locale.ENGLISH to "Until then 🎸",
                    Locale.GERMAN to "Bis dahin 🎸",
                )[locale] ?: "???",
            ),
            RoomListElementViewModelMock(
                roomName = "Bruno",
                roomImage = PlatformResource.resource("user8.png"),
                time = "12.06.2024",
                lastMessage = mapOf(
                    Locale.ENGLISH to "I like that idea 😀",
                    Locale.GERMAN to "Ich mag die Idee 😀",
                )[locale] ?: "???",
            ),
            RoomListElementViewModelMock(
                roomName = "Felicitas",
                roomImage = PlatformResource.resource("user11.png"),
                time = "13.06.2024",
                lastMessage = mapOf(
                    Locale.ENGLISH to "Have a nice week!",
                    Locale.GERMAN to "Schöne Woche noch!",
                )[locale] ?: "???",
            ),
        )
    )

    override fun closeProfile() {}
    override fun createNewRoom() {}
    override fun createNewRoomFor(userId: UserId) {}
    override fun errorDismiss() {}

    // this trick is needed for iOS UITesting where we cannot directly manipulate the showRoom variable
    override fun selectRoom(roomId: RoomId) {
        room.value = ChildStack(
            RoomRouter.Config.View(UserId("tammy", "server"), selectedRoom.full),
            RoomRouter.Wrapper.View(RoomViewModelMock(locale))
        )
    }

    override fun sendLogs() {}
    override fun verifyAccount(userId: UserId) {}
}

class AccountViewModelMock : AccountViewModel {
    override val accounts: StateFlow<List<AccountInfo>> = MutableStateFlow(
        listOf(
            AccountInfo(
                userId = UserId("tammy", "server"),
                displayName = "Tammy",
                displayColor = null,
                initials = "T",
                avatar = PlatformResource.resource("logo.png"),
            )
        )
    )
    override val activeAccount: StateFlow<UserId?> = MutableStateFlow(UserId("tammy", "server"))
    override val isSingleAccount: StateFlow<Boolean> = MutableStateFlow(true)
    override val globalNotificationCount: StateFlow<String?> = MutableStateFlow(null)
    override val accountNotificationCounts: StateFlow<Map<UserId, String?>> = MutableStateFlow(mapOf())

    override fun selectActiveAccount(userId: UserId?) {}
    override fun openUserSettings() {}
    override fun openUserAccounts() {}
    override fun openAppInfo() {}
}

class RoomListElementViewModelMock(
    roomName: String,
    roomImage: ByteArray? = null,
    lastMessage: String? = null,
    time: String? = null,
    isDirect: Boolean = true,
    isEncrypted: Boolean = true,
    isPublic: Boolean = false,
    inviterUserInfo: UserInfoElement? = null,
    unreadMessages: Int? = null,
    presence: Presence? = null,
) : RoomListElementViewModel {
    override val account: UserId = UserId("alice", "server")
    override val accountColor: StateFlow<Long?> = MutableStateFlow(null)
    override val rejectInvitationInProgress: StateFlow<Boolean> = MutableStateFlow(false)

    override val error: StateFlow<String?> = MutableStateFlow(null)
    override val inviterUserInfo: StateFlow<UserInfoElement?> = MutableStateFlow(inviterUserInfo)
    override val isDirect: StateFlow<Boolean?> = MutableStateFlow(isDirect)
    override val isEncrypted: StateFlow<Boolean?> = MutableStateFlow(isEncrypted)
    override val isInvite: StateFlow<Boolean?> = MutableStateFlow(inviterUserInfo != null)
    override val isLeave: StateFlow<Boolean?> = MutableStateFlow(false)
    override val isKnock: StateFlow<Boolean?> = MutableStateFlow(false)
    override val isPublic: StateFlow<Boolean?> = MutableStateFlow(isPublic)
    override val lastMessage: StateFlow<String?> = MutableStateFlow(lastMessage)
    override val usersTyping: StateFlow<String?> = MutableStateFlow(null)
    override val presence: StateFlow<Presence?> = MutableStateFlow(presence)
    override val roomId: RoomId = RoomId(Random.nextString(12))
    override val isLoaded: StateFlow<Boolean> = MutableStateFlow(true)
    override val roomImage: StateFlow<ByteArray?> = MutableStateFlow(roomImage)
    override val roomImageInitials: StateFlow<String?> = MutableStateFlow(inviterUserInfo?.initials ?: "")
    override val roomName: StateFlow<String?> = MutableStateFlow(roomName)
    override val time: StateFlow<String?> = MutableStateFlow(time)
    override val isUnread: StateFlow<Boolean?> = MutableStateFlow(false)
    override val notificationCount: StateFlow<String?> = MutableStateFlow(null)

    override fun acceptInvitation() {}
    override fun clearError() {}
    override fun markUnread() {}
    override fun markRead() {}

    override fun rejectInvitation() {}
    override fun rejectInvitationAndBlockInviter() {}
    override fun unknock() {}
    override fun forgetRoom() {}
}

class RoomViewModelMock(locale: Locale) : RoomViewModel {
    override val timelineStack: Value<ChildStack<TimelineRouter.Config, TimelineRouter.Wrapper>> =
        MutableValue(
            ChildStack(
                TimelineRouter.Config.View(selectedRoom.full),
                TimelineRouter.Wrapper.View(TimelineViewModelMock(locale))
            )
        )
    override val extrasStack: Value<ChildStack<ExtrasRouter.Config, ExtrasRouter.Wrapper>> =
        MutableValue(ChildStack(ExtrasRouter.Config.None, ExtrasRouter.Wrapper.None))

    override fun closeRoom() {}
    override fun openRoomSettings() {}
    override fun openUserProfile(userId: UserId) {}
    override fun openTimelineElementMetadata(eventId: EventId) {}
}

class TimelineViewModelMock(
    locale: Locale,
) : TimelineViewModel {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val draggedFile: StateFlow<FileDescriptor?> = MutableStateFlow(null)
    override val unreadCount: StateFlow<String?> = MutableStateFlow(null)
    override val error: StateFlow<String?> = MutableStateFlow(null)

    override val inputAreaViewModel: InputAreaViewModel = InputAreaViewModelModelMock()

    override val isDirect: StateFlow<Boolean> = MutableStateFlow(false)
    override val reportMessageStack: Value<ChildStack<ReportMessageRouter.Config, ReportMessageRouter.Wrapper>> =
        MutableValue(ChildStack(ReportMessageRouter.Config.None, ReportMessageRouter.Wrapper.None))
    override val roomHeaderViewModel: RoomHeaderViewModel = RoomHeaderViewModelMock(locale)
    override val sendAttachmentStack: Value<ChildStack<TimelineViewModel.Config, TimelineViewModel.Wrapper>> =
        MutableValue(ChildStack(TimelineViewModel.Config.None, TimelineViewModel.Wrapper.None))
    override val elements: StateFlow<List<BaseTimelineElementHolderViewModel>> =
        MutableStateFlow(
            listOf(
                TimelineElementHolderViewModelMock(
                    sender = UserInfoElement(UserId("tammy", "server"), "Tammy", "T"),
                    element = TextMessageViewModelMock(
                        mapOf(
                            Locale.ENGLISH to "In one week it's that time again! We're having another little Board Games Night soon.",
                            Locale.GERMAN to "In einer Woche ist es wieder so weit! Wir veranstalten bald wieder eine kleine Brettspielnacht.",
                        )[locale] ?: "???"
                    ),
                    formattedTime = "08:12",
                    isByMe = true,
                    showSender = false,
                    isFirstInUserSequence = true,
                ),
                TimelineElementHolderViewModelMock(
                    sender = UserInfoElement(UserId("henry", "server"), "Henry", "H"),
                    element = TextMessageViewModelMock(
                        mapOf(
                            Locale.ENGLISH to "Oh right, I almost forgot about that.",
                            Locale.GERMAN to "Ach ja, das hätte ich fast vergessen.",
                        )[locale] ?: "???"
                    ),
                    formattedTime = "08:37",
                    isByMe = false,
                    showSender = true,
                    isFirstInUserSequence = true,
                ),
                TimelineElementHolderViewModelMock(
                    sender = UserInfoElement(UserId("henry", "server"), "Henry", "H"),
                    element = TextMessageViewModelMock(
                        mapOf(
                            Locale.ENGLISH to "Is everything already organized, or can I still help with something?",
                            Locale.GERMAN to "Ist schon alles organisiert oder kann ich noch etwas mithelfen?",
                        )[locale] ?: "???"
                    ),
                    formattedTime = "08:38",
                    isByMe = false,
                    showSender = false,
                    isFirstInUserSequence = false,
                ),
                TimelineElementHolderViewModelMock(
                    sender = UserInfoElement(UserId("henry", "server"), "Henry", "H"),
                    element = TextMessageViewModelMock(
                        mapOf(
                            Locale.ENGLISH to "Well, everyone is welcome to bring cake or other treats.",
                            Locale.GERMAN to "Nun, jeder kann Kuchen oder andere Leckereien mitzubringen.",
                        )[locale] ?: "???"
                    ),
                    formattedTime = "08:43",
                    isByMe = false,
                    showSender = false,
                    isFirstInUserSequence = false,
                ),
                TimelineElementHolderViewModelMock(
                    sender = UserInfoElement(UserId("michael", "server"), "Michael", "M"),
                    element = TextMessageViewModelMock(
                        mapOf(
                            Locale.ENGLISH to "Great, then I'll bring my famous strawberry cake again.",
                            Locale.GERMAN to "Gut, dann bringe ich wieder meinen berühmten Erdbeerkuchen mit.",
                        )[locale] ?: "???"
                    ),
                    formattedTime = "08:31",
                    isByMe = false,
                    showSender = true,
                    isFirstInUserSequence = true,
                    repliedElement = TimelineElementHolderViewModelMock(
                        sender = UserInfoElement(UserId("henry", "server"), "Henry", "H"),
                        element = TextMessageViewModelMock(
                            mapOf(
                                Locale.ENGLISH to "Well, everyone is welcome to bring cake or other treats.",
                                Locale.GERMAN to "Nun, jeder kann Kuchen oder andere Leckereien mitzubringen.",
                            )[locale] ?: "???"
                        ),
                        formattedTime = "08:43",
                        isByMe = false,
                        showSender = true,
                        isFirstInUserSequence = true,
                    )
                ),
                TimelineElementHolderViewModelMock(
                    sender = UserInfoElement(UserId("michael", "server"), "Michael", "M"),
                    element = ImageMessageViewModelMock(
                        PlatformResource.resource("strawberrycake.png"), caption = mapOf(
                            Locale.ENGLISH to "Great, then I'll bring my famous strawberry cake again.",
                            Locale.GERMAN to "Gut, dann bringe ich wieder meinen berühmten Erdbeerkuchen mit.",
                        )[locale] ?: "???", 1024, 1024
                    ),
                    formattedTime = "08:50",
                    isByMe = false,
                    showSender = false,
                    isFirstInUserSequence = false,
                    repliedElement = TimelineElementHolderViewModelMock(
                        sender = UserInfoElement(UserId("henry", "server"), "Henry", "H"),
                        element = TextMessageViewModelMock(
                            mapOf(
                                Locale.ENGLISH to "Well, everyone is welcome to bring cake or other treats.",
                                Locale.GERMAN to "Nun, jeder kann Kuchen oder andere Leckereien mitzubringen.",
                            )[locale] ?: "???"
                        ),
                        formattedTime = "08:43",
                        isByMe = false,
                        showSender = true,
                        isFirstInUserSequence = true,
                    )
                ),
                TimelineElementHolderViewModelMock(
                    sender = UserInfoElement(UserId("tina", "server"), "Tina", "T"),
                    element = TextMessageViewModelMock(
                        mapOf(
                            Locale.ENGLISH to "😋\nDoes anyone have ideas on what we should play?",
                            Locale.GERMAN to "😋\nHat jemand eine Idee, was wir spielen können?",
                        )[locale] ?: "???"
                    ),
                    formattedTime = "08:43",
                    isByMe = false,
                    showSender = true,
                    isFirstInUserSequence = true,
                ),
                TimelineElementHolderViewModelMock(
                    sender = UserInfoElement(UserId("tammy", "server"), "Tammy", "T"),
                    element = TextMessageViewModelMock(
                        mapOf(
                            Locale.ENGLISH to "Oh, I have a lot of options here. But since we are quite a few people, some games won't work. " +
                                    "I'll take a look and see what would fit well! I'm already excited!",
                            Locale.GERMAN to "Oh, ich habe hier eine Menge Möglichkeiten. Aber da wir ziemlich viele Leute sind, werden einige Spiele nicht funktionieren. " +
                                    "Ich werde mal schauen, was gut passen würde! Ich bin schon ganz hibbelig!",
                        )[locale] ?: "???"
                    ),
                    formattedTime = "08:49",
                    isByMe = true,
                    showSender = false,
                    reactions = mapOf("👍" to 3, "🫠" to 1),
                    isFirstInUserSequence = true,
                ),
                TimelineElementHolderViewModelMock(
                    sender = UserInfoElement(UserId("michael", "server"), "Michael", "M"),
                    element = TextMessageViewModelMock(
                        mapOf(
                            Locale.ENGLISH to "Oh, very cool. I'm really looking forward to it!",
                            Locale.GERMAN to "Oh, sehr cool. Ich freue mich schon sehr darauf!",
                        )[locale] ?: "???"
                    ),
                    formattedTime = "08:50",
                    isByMe = false,
                    showSender = true,
                    isFirstInUserSequence = true,
                ),
                TimelineElementHolderViewModelMock(
                    sender = UserInfoElement(UserId("tammy", "server"), "Tammy", "T"),
                    element = TextMessageViewModelMock(
                        mapOf(
                            Locale.ENGLISH to "Alright, see you next week at the latest!",
                            Locale.GERMAN to "Alles klar, dann bis spätestens nächste Woche!",
                        )[locale] ?: "???"
                    ),
                    formattedTime = "09:01",
                    isByMe = true,
                    showSender = false,
                    isFirstInUserSequence = true,
                ),
            )
        )
    override val canLoadBefore: StateFlow<Boolean?> = MutableStateFlow(false)
    override val canLoadAfter: StateFlow<Boolean?> = MutableStateFlow(false)
    override val scrollTo: StateFlow<String?> = elements.map { it.last().key }
        .stateIn(coroutineScope, SharingStarted.Lazily, null)

    override fun setViewState(viewState: TimelineViewModel.ViewState) {}
    override fun errorDismiss() {}
    override fun jumpToEndOfTimeline() {}
    override suspend fun loadBefore() {}
    override fun leaveRoom() {}
    override suspend fun loadAfter() {}
    override suspend fun dropBefore(key: String) {}
    override suspend fun dropAfter(key: String) {}
    override suspend fun markAsRead(key: String) {}
    override suspend fun finishedScrollTo(key: String) {
        TODO("Not yet implemented")
    }
}

class RoomHeaderViewModelMock(locale: Locale) : RoomHeaderViewModel {
    override val canBlockUser: StateFlow<Boolean> = MutableStateFlow(false)
    override val canUnblockUser: StateFlow<Boolean> = MutableStateFlow(false)
    override val canVerifyUser: StateFlow<Boolean> = MutableStateFlow(false)
    override val knockingMembersCount: StateFlow<Int> = MutableStateFlow(0)
    override val error: StateFlow<String?> = MutableStateFlow(null)
    override val isUserBlocked: StateFlow<Boolean> = MutableStateFlow(false)
    override val isDirectChat: StateFlow<Boolean> = MutableStateFlow(false)
    override val roomHeaderInfo: StateFlow<RoomHeaderInfo> = MutableStateFlow(
        RoomHeaderInfo(
            roomName = mapOf(
                Locale.ENGLISH to "Board Games Night",
                Locale.GERMAN to "Brettspielnacht",
            )[locale] ?: "???",
            roomTopic = mapOf(
                Locale.ENGLISH to "Organizing our monthly Board Games Night",
                Locale.GERMAN to "Organisation unserer monatlichen Brettspielnacht",
            )[locale] ?: "???",
            roomImageInitials = "BG",
            roomImage = PlatformResource.resource("boardgame.png"),
            presence = null,
            isEncrypted = true,
            isPublic = false,
            isLeave = false,
        )
    )
    override val userTrustLevel: StateFlow<UserTrustLevel?> = MutableStateFlow(null)
    override val usersTyping: StateFlow<String?> = MutableStateFlow(null)

    override fun blockUser() {}
    override fun unblockUser() {}
    override fun verifyUser() {}
    override fun openRoomSettings() {}
    override fun openUserProfile() {}
    override fun back() {}
}

class InputAreaViewModelModelMock : InputAreaViewModel {
    override val hasShownAttachmentSelectDialog: SharedFlow<Boolean> = MutableSharedFlow()
    override val isReplace: StateFlow<Boolean> = MutableStateFlow(false)
    override val isReply: StateFlow<Boolean> = MutableStateFlow(false)
    override val repliedElement: StateFlow<TimelineElementHolderViewModel?> = MutableStateFlow(null)
    override val isAllowedToSendMessages: StateFlow<Boolean> = MutableStateFlow(true)
    override val textField: TextFieldViewModel = TextFieldViewModelImpl(100, "Don't forget the games 😉")
    override val isSendEnabled: StateFlow<Boolean> = MutableStateFlow(true)
    @Deprecated("use suggestedMentions instead", replaceWith = ReplaceWith("suggestedMentions"))
    override val listOfMentions: StateFlow<List<UserInfoElement>?> = MutableStateFlow(null)
    @Deprecated("use suggestedMentionsLoading instead", replaceWith = ReplaceWith("suggestedMentionsLoading"))
    override val listOfMentionsLoading: StateFlow<Boolean> = MutableStateFlow(false)
    override val suggestedMentions: StateFlow<List<InputAreaViewModel.SuggestedMention>?> = MutableStateFlow(null)
    override val suggestedMentionsLoading: StateFlow<Boolean> = MutableStateFlow(false)
    override val useMarkdown: StateFlow<Boolean> = MutableStateFlow(false)

    @OptIn(TrixnityMessengerPrivateApi::class)
    override val audio: AudioRecordingAreaViewModel = object : AudioRecordingAreaViewModel {
        override val recorder: AudioRecorderViewModel? = null
        override val capturePlayer: StateFlow<MediaPlayerViewModel?> = MutableStateFlow(null)
        override fun sendAudioMessage() {}
        override fun deleteAudioMessage() {}
        override fun loadAudioMessage(content: RoomMessageEventContent.FileBased.Audio) {}
    }
    override val showAttachmentSelectDialog: StateFlow<Boolean> = MutableStateFlow(false)

    @Deprecated("use selectionMention with id instead")
    override fun selectMention(userId: UserId) {}
    override fun selectMention(id: String) {}

    override fun closeAttachmentDialog() {}
    override fun onAttachmentFileSelect(file: FileDescriptor) {}
    override fun replaceMessage(roomId: RoomId, eventId: EventId) {}
    override fun cancelReplace() {}
    override fun replyMessage(roomId: RoomId, eventId: EventId) {}
    override fun cancelReply() {}
    override fun selectAttachment() {}
    override fun sendMessage() {}
}

class TimelineElementHolderViewModelMock(
    sender: UserInfoElement,
    element: TimelineElementViewModel<*>,
    override val formattedTime: String,
    override val isByMe: Boolean,
    showSender: Boolean,
    isFirstInUserSequence: Boolean,
    reactions: Map<String, Int> = mapOf(),
    repliedElement: TimelineElementHolderViewModel? = null,
) : TimelineElementHolderViewModel {
    override val key: String = Random.nextString(12)
    override val roomId: RoomId = selectedRoom
    override val eventId: EventId = EventId(key)
    override val showUnreadMarker: StateFlow<Boolean> = MutableStateFlow(false)
    override val canBeEdited: StateFlow<Boolean> = MutableStateFlow(false)
    override val canBeRedacted: StateFlow<Boolean> = MutableStateFlow(false)
    override val canBeRepliedTo: StateFlow<Boolean> = MutableStateFlow(false)
    override val canBeReported: StateFlow<Boolean> = MutableStateFlow(false)
    override val highlight: StateFlow<Boolean> = MutableStateFlow(false)
    override val isRead: StateFlow<Boolean> = MutableStateFlow(true)
    override val readers: StateFlow<List<UserInfoElement>?> = MutableStateFlow(listOf())
    override val isReplaced: StateFlow<Boolean> = MutableStateFlow(false)
    override val redactionError: StateFlow<String?> = MutableStateFlow(null)
    override val showRedactionWarning: StateFlow<Boolean> = MutableStateFlow(false)
    @Deprecated("when supported by the homeserver, redactions are applied immediately")
    override val redactionInProgress: StateFlow<Boolean> = MutableStateFlow(false)
    override val showLoadingIndicatorAfter: StateFlow<Boolean> = MutableStateFlow(false)
    override val showLoadingIndicatorBefore: StateFlow<Boolean> = MutableStateFlow(false)
    override val reactions: StateFlow<EventReactions?> =
        MutableStateFlow(
            EventReactions(
                buildSet {
                    reactions.forEach { (reaction, number) ->
                        repeat(number) {
                            add(
                                EventReaction(
                                    reaction,
                                    UserInfoElement(UserId(number.toString(), "server"), number.toString(), ""),
                                    EventIdOrTransactionId(number.toString()),
                                    false,
                                )
                            )
                        }
                    }
                }
            )
        )
    override val canBeReactedTo: StateFlow<Boolean> = MutableStateFlow(false)
    override val element: StateFlow<TimelineElementViewModel<*>?> = MutableStateFlow(element)
    override val isReply: StateFlow<Boolean?> = MutableStateFlow(false)
    override val repliedElement: StateFlow<TimelineElementHolderViewModel?> = MutableStateFlow(repliedElement)
    override val isFirstInUserSequence: StateFlow<Boolean?> = MutableStateFlow(isFirstInUserSequence)
    override val formattedDate: String = "24.11.2025"
    override val isSent: StateFlow<Boolean> = MutableStateFlow(true)
    override val sender: StateFlow<UserInfoElement?> = MutableStateFlow(sender)
    override val showSender: StateFlow<Boolean?> = MutableStateFlow(showSender)
    override val showBigGapBefore: StateFlow<Boolean?> = MutableStateFlow(false)
    override val sendError: StateFlow<String?> = MutableStateFlow(null)

    override fun redact() {}
    override fun acceptRedactionWarning() {}
    override fun cancelRedactionWarning() {}
    override fun reply() {}
    override fun endReply() {}
    override fun report() {}
    override fun addReaction(reaction: String) {}
    override fun removeReaction(reaction: String) {}
    override fun openTimelineElementMetadata() {}
    override fun replace() {}
    override fun endReplace() {}
    override fun jumpTo() {}
}

class TextMessageViewModelMock(
    override val body: String,
) : RoomMessageTimelineElementViewModel.TextBased.Text {
    override val formattedBody: String? = null
    override val formattedBodyContent: HtmlNode.HtmlElement =
        HtmlNode.HtmlElement("#root", emptyMap(), listOf(HtmlNode.TextContent(body)))
    override val mentionsInBody: Map<IntRange, StateFlow<TimelineElementMention?>> = mapOf()
    override val mentionsInFormattedBody: StateFlow<Map<String, TimelineElementMention?>> = MutableStateFlow(mapOf())
    override fun openMention(mention: TimelineElementMention) {}
    override val isMentioned: Boolean = false
}

class ImageMessageViewModelMock(
    thumbnail: ByteArray?,
    caption: String?,
    override val height: Int,
    override val width: Int,
) : RoomMessageTimelineElementViewModel.FileBased.Image {
    override val thumbnail: StateFlow<ByteArray?> = MutableStateFlow(thumbnail)
    override val thumbnailLoading: StateFlow<Boolean> = MutableStateFlow(false)
    override val thumbnailWidth: Int? = null
    override val thumbnailHeight: Int? = null
    override val thumbnailAutoDownloadLimit: Long = 0L

    @MSC2448
    override val blurhash: String? = null
    override val name: String = "image.jpg"
    override val body: String = caption ?: name
    override val formattedBody: String? = null
    override val formattedBodyContent: HtmlNode.HtmlElement? =
        HtmlNode.HtmlElement("#root", emptyMap(), listOf(HtmlNode.TextContent(body)))
    override val mentionsInBody: Map<IntRange, StateFlow<TimelineElementMention?>> = mapOf()
    override val mentionsInFormattedBody: StateFlow<Map<String, TimelineElementMention?>> = MutableStateFlow(mapOf())

    override val size: String? = null
    override val mimeType: String? = null
    override val hasCaption: Boolean = caption != null
    override val loadMediaResult: StateFlow<ByteArray?> = MutableStateFlow(null)
    override val loadMediaResultPlatformMedia: StateFlow<PlatformMedia?> = MutableStateFlow(null)
    override val loadMediaResultBytes: StateFlow<ByteArray?> = MutableStateFlow(thumbnail)
    override val loadMediaProgress: StateFlow<FileTransferProgressElement?> = MutableStateFlow(null)
    override val loadMediaError: StateFlow<String?> = MutableStateFlow(null)
    override val downloadMediaResult: StateFlow<PlatformMedia?> = MutableStateFlow(null)
    override val downloadMediaProgress: StateFlow<FileTransferProgressElement?> = MutableStateFlow(null)
    override val downloadMediaError: StateFlow<String?> = MutableStateFlow(null)
    override val saveDialogOpen: StateFlow<Boolean> = MutableStateFlow(false)

    override fun loadMedia() {}
    override fun cancelLoadMedia() {}
    override fun downloadMedia(processFile: suspend (PlatformMedia) -> Unit, onDownloadCancelled: () -> Unit) {}
    override fun cancelDownloadMedia() {}
    override fun showSaveDialog() {}
    override fun hideSaveDialog(errorShown: Boolean) {}
    override fun openMention(mention: TimelineElementMention) {}
    override val isMentioned: Boolean = false
}
