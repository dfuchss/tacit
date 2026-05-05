package org.fuchss.matrix.tacit

import de.connect2x.trixnity.messenger.compose.view.i18n.I18nView
import de.connect2x.trixnity.messenger.compose.view.room.RoomView
import de.connect2x.trixnity.messenger.compose.view.room.settings.ChangeRoomAvatarView
import de.connect2x.trixnity.messenger.compose.view.room.settings.RoomSettingsView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.InputAreaView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.RoomHeaderView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.TimelineView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.TypingIndicatorView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.ReadMarkerView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.TimelineElementView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.EmoteRoomMessageTimelineElementView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.NoticeRoomMessageTimelineElementView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.TextRoomMessageTimelineElementView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.bubble.MessageBubbleView
import de.connect2x.trixnity.messenger.compose.view.roomlist.RoomListContainerView
import de.connect2x.trixnity.messenger.compose.view.roomlist.RoomListView
import de.connect2x.trixnity.messenger.compose.view.roomlist.header.AccountOptionsView
import de.connect2x.trixnity.messenger.compose.view.roomlist.header.ShowSearchView
import de.connect2x.trixnity.messenger.compose.view.root.MainView
import de.connect2x.trixnity.messenger.compose.view.root.MessengerView
import de.connect2x.trixnity.messenger.compose.view.settings.*
import de.connect2x.trixnity.messenger.viewmodel.room.settings.ChangeRoomAvatarViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.settings.RoomSettingsViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.InputAreaViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListElementViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.settings.AppearanceSettingsViewModelFactory
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListElementViewModelFactory
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListViewModelFactory
import org.fuchss.matrix.tacit.viewmodel.room.settings.TacitChangeRoomAvatarViewModelFactory
import org.fuchss.matrix.tacit.viewmodel.room.settings.TacitRoomSettingsViewModelFactory
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitInputAreaViewModelFactory
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitTimelineViewModelFactory
import org.fuchss.matrix.tacit.viewmodel.settings.TacitAppearanceSettingsViewModelFactory
import org.fuchss.matrix.tacit.views.*
import org.fuchss.matrix.tacit.views.i18n.TacitI18nView
import org.fuchss.matrix.tacit.views.room.TacitRoomHeaderView
import org.fuchss.matrix.tacit.views.room.TacitRoomSettingsView
import org.fuchss.matrix.tacit.views.room.TacitRoomView
import org.fuchss.matrix.tacit.views.room.list.TacitRoomListContainerView
import org.fuchss.matrix.tacit.views.room.list.TacitRoomListView
import org.fuchss.matrix.tacit.views.room.settings.TacitChangeRoomAvatarView
import org.fuchss.matrix.tacit.views.room.timeline.*
import org.fuchss.matrix.tacit.views.settings.*
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

fun tammyTacitModule() = module {

    single<RoomListView> { TacitRoomListView() }
    single<RoomListContainerView> { TacitRoomListContainerView() }
    single<TacitI18nView> { TacitI18nView(get(), get(), get(), get()) }
    single<I18nView> { get<TacitI18nView>() }
    single<RoomListViewModelFactory> { TacitRoomListViewModelFactory }
    single<RoomListElementViewModelFactory> { TacitRoomListElementViewModelFactory }
    single<TimelineViewModelFactory> { TacitTimelineViewModelFactory }
    single<InputAreaViewModelFactory> { TacitInputAreaViewModelFactory }
    single<AppearanceSettingsViewModelFactory> { TacitAppearanceSettingsViewModelFactory }
    single<RoomSettingsViewModelFactory> { TacitRoomSettingsViewModelFactory }
    single<ChangeRoomAvatarViewModelFactory> { TacitChangeRoomAvatarViewModelFactory }
    single<MainView> { TacitMainView() }
    single<MessengerView> { TacitMessengerView() }
    single<ShowSearchView> { TacitShowSearchView() }
    single<AccountOptionsView> { TacitAccountOptionsView() }
    single<RoomView> { TacitRoomView() }
    single<RoomHeaderView> { TacitRoomHeaderView() }
    single<RoomSettingsView> { TacitRoomSettingsView() }
    single<ChangeRoomAvatarView> { TacitChangeRoomAvatarView() }
    single<TypingIndicatorView> { TacitTypingIndicatorView() }
    single<InputAreaView> { TacitInputAreaView() }
    single<ReadMarkerView> { TacitReadMarkerView() }
    single<TextRoomMessageTimelineElementView>(named<TextRoomMessageTimelineElementView>()) {
        TacitTextRoomMessageTimelineElementViewImpl()
    }.bind<TimelineElementView<*>>()
    single<NoticeRoomMessageTimelineElementView>(named<NoticeRoomMessageTimelineElementView>()) {
        TacitNoticeRoomMessageTimelineElementViewImpl()
    }.bind<TimelineElementView<*>>()
    single<EmoteRoomMessageTimelineElementView>(named<EmoteRoomMessageTimelineElementView>()) {
        TacitEmoteRoomMessageTimelineElementViewImpl()
    }.bind<TimelineElementView<*>>()
    single<MessageBubbleView> { TacitFlatMessageView() }
    single<TimelineView> { TacitTimelineView() }
    single<AccountSetupWizardStepList> { TacitAccountSetupWizardStepList() }
    single<UserSettingsView> { TacitUserSettingsView() }
    single<AppearanceSettingsView> { TacitAppearanceSettingsView() }
    single<AppInfoView> { TacitAppInfoView() }
    single<LegalFooterView> { TacitLegalFooterView() }
}
