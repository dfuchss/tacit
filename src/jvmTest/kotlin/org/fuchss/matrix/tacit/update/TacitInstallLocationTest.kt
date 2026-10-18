package org.fuchss.matrix.tacit.update

import org.fuchss.matrix.tacit.Flavor
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TacitInstallLocationTest {
    private fun resolve(
        appPath: String?,
        os: TacitOs?,
        flavor: Flavor = Flavor.PROD,
        isWritable: (Path) -> Boolean = { true },
    ) = TacitInstallLocation.resolve(appPath, os, flavor, isWritable)

    @Test
    fun derivesTheInstallRootPerOs() {
        assertEquals(
            TacitInstallLocation.SelfUpdatable(TacitOs.MACOS, Paths.get("/Applications/Tacit.app")),
            resolve("/Applications/Tacit.app/Contents/MacOS/Tacit", TacitOs.MACOS),
        )
        assertEquals(
            TacitInstallLocation.SelfUpdatable(TacitOs.WINDOWS, Paths.get("/Users/me/Apps/Tacit")),
            resolve("/Users/me/Apps/Tacit/Tacit.exe", TacitOs.WINDOWS),
        )
        assertEquals(
            TacitInstallLocation.SelfUpdatable(TacitOs.LINUX, Paths.get("/home/me/Tacit")),
            resolve("/home/me/Tacit/bin/Tacit", TacitOs.LINUX),
        )
    }

    @Test
    fun fallsBackToManualOnly() {
        // not a jpackage image (e.g. ./gradlew run)
        assertIs<TacitInstallLocation.ManualOnly>(resolve(null, TacitOs.MACOS))
        assertIs<TacitInstallLocation.ManualOnly>(resolve("", TacitOs.LINUX))
        assertIs<TacitInstallLocation.ManualOnly>(
            resolve("/Applications/Tacit.app/Contents/MacOS/Tacit", TacitOs.MACOS, flavor = Flavor.DEV)
        )
        assertIs<TacitInstallLocation.ManualOnly>(resolve("/Volumes/Tacit/Tacit.app/Contents/MacOS/Tacit", TacitOs.MACOS))
        assertIs<TacitInstallLocation.ManualOnly>(
            resolve(
                "/private/var/folders/xy/T/AppTranslocation/1234/d/Tacit.app/Contents/MacOS/Tacit",
                TacitOs.MACOS,
            )
        )
        assertIs<TacitInstallLocation.ManualOnly>(resolve("/opt/tacit/Tacit", TacitOs.LINUX))
        assertIs<TacitInstallLocation.ManualOnly>(resolve("/usr/local/bin/Tacit", TacitOs.MACOS))
        assertIs<TacitInstallLocation.ManualOnly>(resolve("/home/me/Tacit/bin/Tacit", null))
    }

    @Test
    fun requiresTheInstallAndItsParentToBeWritable() {
        val root = Paths.get("/Applications/Tacit.app")
        assertIs<TacitInstallLocation.ManualOnly>(
            resolve("/Applications/Tacit.app/Contents/MacOS/Tacit", TacitOs.MACOS) { it != root }
        )
        assertIs<TacitInstallLocation.ManualOnly>(
            resolve("/Applications/Tacit.app/Contents/MacOS/Tacit", TacitOs.MACOS) { it != root.parent }
        )
    }
}
