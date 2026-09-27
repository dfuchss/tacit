package org.fuchss.matrix.tacit

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.fuchss.matrix.tacit.screenshot.Locale
import org.fuchss.matrix.tacit.screenshot.ScreenshotView
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.cleanstatusbar.BluetoothState
import tools.fastlane.screengrab.cleanstatusbar.CleanStatusBar
import tools.fastlane.screengrab.cleanstatusbar.IconVisibility
import tools.fastlane.screengrab.cleanstatusbar.MobileDataType
import tools.fastlane.screengrab.locale.LocaleTestRule
import tools.fastlane.screengrab.locale.LocaleUtil
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TimmyClientTest {

    @Rule
    @JvmField
    val localeTestRule = LocaleTestRule()

    @BeforeTest
    fun before() {
        CleanStatusBar()
            .setBluetoothState(BluetoothState.DISCONNECTED)
            .setMobileNetworkDataType(MobileDataType.LTE)
            .setWifiVisibility(IconVisibility.HIDE)
            .setShowNotifications(false)
            .setClock("0900")
            .setBatteryLevel(100)
            .enable()
    }

    @AfterTest
    fun after() {
        CleanStatusBar.disable()
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun timmyScreen() = runTest {
        runComposeUiTest {
            val showRoom = MutableStateFlow(false)
            setContent {
                ScreenshotView(
                    when ((LocaleUtil.getTestLocale() ?: "en-US").substringBefore("-")) {
                        "de" -> Locale.GERMAN
                        else -> Locale.ENGLISH
                    },
                    showRoom,
                )
            }
            showRoom.value = false
            waitForIdle()
            Thread.sleep(5000)
            Screengrab.screenshot("room_list")
            showRoom.value = true
            waitForIdle()
            Thread.sleep(5000)
            Screengrab.screenshot("room")
        }
    }
}