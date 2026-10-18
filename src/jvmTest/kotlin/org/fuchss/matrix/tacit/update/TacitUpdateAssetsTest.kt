package org.fuchss.matrix.tacit.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TacitUpdateAssetsTest {
    @Test
    fun selectsTheAssetForOsAndArchitecture() {
        assertEquals(
            TacitUpdateAsset("tacit-macos-arm64.dmg", TacitUpdatePackage.DMG),
            TacitUpdateAssets.forPlatform(TacitOs.fromOsName("Mac OS X"), "aarch64"),
        )
        assertEquals(
            TacitUpdateAsset("tacit-windows-x64.zip", TacitUpdatePackage.ZIP),
            TacitUpdateAssets.forPlatform(TacitOs.fromOsName("Windows 11"), "amd64"),
        )
        assertEquals(
            TacitUpdateAsset("tacit-linux-x64.zip", TacitUpdatePackage.ZIP),
            TacitUpdateAssets.forPlatform(TacitOs.fromOsName("Linux"), "amd64"),
        )
    }

    @Test
    fun unsupportedPlatformsHaveNoAsset() {
        assertNull(TacitUpdateAssets.forPlatform(TacitOs.MACOS, "x86_64"))
        assertNull(TacitUpdateAssets.forPlatform(TacitOs.WINDOWS, "aarch64"))
        assertNull(TacitUpdateAssets.forPlatform(TacitOs.LINUX, "aarch64"))
        assertNull(TacitUpdateAssets.forPlatform(TacitOs.fromOsName("FreeBSD"), "amd64"))
    }

    @Test
    fun parsesSha256SumsFromTheReleaseWorkflow() {
        val dmg = "a".repeat(64)
        val zip = "B".repeat(64)
        val sums = TacitSha256Sums.parse(
            """
            $dmg  Tacit-26.9.2-tacit.2-macos-arm64.dmg
            $zip *tacit-windows-x64.zip
            ${"c".repeat(64)}  latest/tacit-linux-x64.zip
            not a checksum line
            ${"d".repeat(63)}  too-short.zip

            """.trimIndent()
        )
        assertEquals(dmg, sums["Tacit-26.9.2-tacit.2-macos-arm64.dmg"])
        assertEquals("b".repeat(64), sums["tacit-windows-x64.zip"])
        assertEquals("c".repeat(64), sums["tacit-linux-x64.zip"])
        assertEquals(3, sums.size)
        assertTrue("too-short.zip" !in sums)
    }
}
