package org.fuchss.matrix.tacit.update

import org.fuchss.matrix.tacit.Flavor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

sealed interface TacitInstallLocation {
    /** [root] is what gets swapped: the `Tacit.app` bundle, the folder of `Tacit.exe`, or `bin/..` on Linux. */
    data class SelfUpdatable(val os: TacitOs, val root: Path) : TacitInstallLocation

    /** The update can only be installed by hand from the release page. */
    data class ManualOnly(val reason: String) : TacitInstallLocation

    companion object {
        fun current(): TacitInstallLocation = resolve(
            appPath = System.getProperty("jpackage.app-path"),
            os = TacitOs.current,
            flavor = org.fuchss.matrix.tacit.BuildConfig.flavor,
        )

        fun resolve(
            appPath: String?,
            os: TacitOs?,
            flavor: Flavor,
            isWritable: (Path) -> Boolean = Files::isWritable,
        ): TacitInstallLocation {
            if (flavor == Flavor.DEV) return ManualOnly("development build")
            if (appPath.isNullOrBlank()) return ManualOnly("not running from a packaged app")
            if (os == null) return ManualOnly("unsupported OS")

            val executable = Paths.get(appPath).toAbsolutePath().normalize()
            val root = when (os) {
                TacitOs.MACOS -> executable.parent?.parent?.parent?.takeIf {
                    it.fileName?.toString()?.endsWith(".app") == true &&
                        executable.parent?.fileName?.toString() == "MacOS"
                }

                TacitOs.WINDOWS -> executable.parent
                TacitOs.LINUX -> executable.parent?.takeIf { it.fileName?.toString() == "bin" }?.parent
            } ?: return ManualOnly("unexpected install layout")

            val rootString = root.toString()
            if (rootString.startsWith("/Volumes/")) return ManualOnly("running from a disk image")
            if (rootString.contains("AppTranslocation")) return ManualOnly("running translocated")

            val parent = root.parent ?: return ManualOnly("install at the file system root")
            if (!isWritable(root) || !isWritable(parent)) return ManualOnly("install location is not writable")
            return SelfUpdatable(os, root)
        }
    }
}
