package de.connect2x.tammy

import de.connect2x.trixnity.messenger.multi.MatrixMultiMessengerConfiguration
import de.connect2x.trixnity.messenger.notification.fcm.addFcmPushNotificationProvider
import de.connect2x.trixnity.messenger.notification.unifiedpush.addUnifiedPushNotificationProvider

fun MatrixMultiMessengerConfiguration.addNotificationProviders() {
    addFcmPushNotificationProvider(
        pushUrl = "https://sygnal.demo.timmy-messenger.de/_matrix/push/v1/notify",
        pushAppId = "$appId.fcm",
    )
    addUnifiedPushNotificationProvider(
        pushUrl = "https://matrix.gateway.unifiedpush.org/_matrix/push/v1/notify",
        pushAppId = "$appId.unifiedpush",
    )
}
