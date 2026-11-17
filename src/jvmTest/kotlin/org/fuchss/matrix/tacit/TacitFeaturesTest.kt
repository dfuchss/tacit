package org.fuchss.matrix.tacit

import de.connect2x.trixnity.messenger.MatrixMessengerConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class TacitFeaturesTest {
    @Test
    fun callsAndRoom3StayOff() {
        val defaults = MatrixMessengerConfiguration.Features()
        val features = tacitFeatures(defaults)
        assertFalse(features.enableVideoCalls)
        assertFalse(features.enableRoom3ForNewAccountsOnWeb)
        // Everything else keeps upstream's default.
        assertEquals(
            defaults,
            features.copy(enableVideoCalls = true, enableRoom3ForNewAccountsOnWeb = true),
        )
    }

    @Test
    fun theMessengerResolvesTheConfigurationWithTacitsFeatures() {
        val configuration = MatrixMessengerConfiguration(appName = "Tacit")
        // Mirrors MatrixMessengerImpl: the configuration first, the module factories after it.
        val koin = koinApplication {
            modules(module { single { configuration } }, tacitFeaturesModule(configuration))
        }.koin
        val resolved = koin.get<MatrixMessengerConfiguration>()
        assertEquals(tacitFeatures(configuration.features), resolved.features)
        assertEquals("Tacit", resolved.appName)
        assertSame(resolved, koin.get<de.connect2x.trixnity.messenger.MatrixMessengerBaseConfiguration>())
    }
}
