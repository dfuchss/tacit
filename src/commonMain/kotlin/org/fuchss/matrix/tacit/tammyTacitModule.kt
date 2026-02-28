package org.fuchss.matrix.tacit

import de.connect2x.trixnity.client.MatrixClientConfiguration
import de.connect2x.trixnity.messenger.ConfigureMatrixClientConfiguration
import de.connect2x.trixnity.messenger.compose.view.room.RoomView
import de.connect2x.trixnity.messenger.compose.view.room.settings.RoomSettingsView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.RoomHeaderView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.ReadMarkerView
import de.connect2x.trixnity.messenger.compose.view.room.timeline.element.message.bubble.MessageBubbleView
import de.connect2x.trixnity.messenger.compose.view.roomlist.RoomListView
import de.connect2x.trixnity.messenger.compose.view.roomlist.header.AccountOptionsView
import de.connect2x.trixnity.messenger.compose.view.roomlist.header.ShowSearchView
import de.connect2x.trixnity.messenger.compose.view.root.MainView
import de.connect2x.trixnity.messenger.compose.view.settings.AccountSetupWizardStepList
import de.connect2x.trixnity.messenger.compose.view.settings.AppInfoView
import de.connect2x.trixnity.messenger.compose.view.settings.AppearanceSettingsView
import de.connect2x.trixnity.messenger.compose.view.settings.LegalFooterView
import de.connect2x.trixnity.messenger.compose.view.settings.UserSettingsView
import de.connect2x.trixnity.messenger.viewmodel.room.timeline.TimelineViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListElementViewModelFactory
import de.connect2x.trixnity.messenger.viewmodel.roomlist.RoomListViewModelFactory
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListElementViewModelFactory
import org.fuchss.matrix.tacit.viewmodel.room.list.TacitRoomListViewModelFactory
import org.fuchss.matrix.tacit.viewmodel.room.timeline.TacitTimelineViewModelFactory
import org.fuchss.matrix.tacit.views.TacitFlatMessageView
import org.fuchss.matrix.tacit.views.TacitMainView
import org.fuchss.matrix.tacit.views.TacitAccountOptionsView
import org.fuchss.matrix.tacit.views.TacitReadMarkerView
import org.fuchss.matrix.tacit.views.TacitShowSearchView
import org.fuchss.matrix.tacit.views.room.TacitRoomHeaderView
import org.fuchss.matrix.tacit.views.room.TacitRoomSettingsView
import org.fuchss.matrix.tacit.views.room.TacitRoomView
import org.fuchss.matrix.tacit.views.room.list.TacitRoomListView
import org.fuchss.matrix.tacit.views.settings.TacitAccountSetupWizardStepList
import org.fuchss.matrix.tacit.views.settings.TacitAppInfoView
import org.fuchss.matrix.tacit.views.settings.TacitAppearanceSettingsView
import org.fuchss.matrix.tacit.views.settings.TacitLegalFooterView
import org.fuchss.matrix.tacit.views.settings.TacitUserSettingsView
import org.koin.dsl.module

fun tammyTacitModule() = module {
    single<ConfigureMatrixClientConfiguration> {
        ConfigureMatrixClientConfiguration {
            deleteRooms = MatrixClientConfiguration.DeleteRooms.OnLeave
        }
    }

    single<RoomListView> { TacitRoomListView() }
    single<RoomListViewModelFactory> { TacitRoomListViewModelFactory }
    single<RoomListElementViewModelFactory> { TacitRoomListElementViewModelFactory }
    single<TimelineViewModelFactory> { TacitTimelineViewModelFactory }
    single<MainView> { TacitMainView() }
    single<ShowSearchView> { TacitShowSearchView() }
    single<AccountOptionsView> { TacitAccountOptionsView() }
    // single<AddMatrixAccountViewModelFactory> { PasswordOnlyAddMatrixAccountViewModelFactory }
    single<RoomView> { TacitRoomView() }
    single<RoomHeaderView> { TacitRoomHeaderView() }
    single<RoomSettingsView> { TacitRoomSettingsView() }
    single<ReadMarkerView> { TacitReadMarkerView() }
    single<MessageBubbleView> { TacitFlatMessageView() }
    single<AccountSetupWizardStepList> { TacitAccountSetupWizardStepList() }
    single<UserSettingsView> { TacitUserSettingsView() }
    single<AppearanceSettingsView> { TacitAppearanceSettingsView() }
    single<AppInfoView> { TacitAppInfoView() }
    single<LegalFooterView> { TacitLegalFooterView() }
}
