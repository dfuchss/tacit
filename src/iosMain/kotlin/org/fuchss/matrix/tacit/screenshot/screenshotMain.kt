package org.fuchss.matrix.tacit.screenshot

import de.connect2x.lognity.api.backend.Backend
import de.connect2x.lognity.backend.DefaultBackend
import de.connect2x.lognity.config.CoreConfigExtension
import de.connect2x.lognity.config.SerializableConfig
import de.connect2x.lognity.config.setDefaultConfig
import org.fuchss.matrix.tacit.tammyConfiguration
import de.connect2x.trixnity.messenger.compose.view.ViewControllerFactory
import de.connect2x.trixnity.messenger.compose.view.startMultiMessenger
import de.connect2x.trixnity.messenger.notification.apns.addApnsPushNotificationProvider
import de.connect2x.trixnity.messenger.util.toByteArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.io.Buffer
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

@Throws(IllegalStateException::class)
fun screenshotMain(args: List<String>, showRoom: MutableStateFlow<Boolean>) {
    val bundle = NSBundle.mainBundle
    val path = bundle.pathForResource("lognity", "json") ?: error("Unable to locate logger config")
    val data = NSData.dataWithContentsOfFile(path)?.toByteArray() ?: error("Unable to read logger config")

    Backend.set(DefaultBackend)
    SerializableConfig uses CoreConfigExtension
    Backend.setDefaultConfig(Buffer().also { it.write(data) })

    try {
        startMultiMessenger(args) {
            tammyConfiguration {
                modulesFactories += {
                    module {
                        single<ViewControllerFactory> {
                            ViewControllerFactory {
                                ScreenshotMessengerViewController(it, showRoom)
                            }
                        }
                    }
                }
            }
        }
    } catch (t: Throwable) {
        throw IllegalStateException(t)
    }
}
