package org.fuchss.matrix.tacit.update

enum class TacitOs {
    MACOS,
    WINDOWS,
    LINUX;

    companion object {
        fun fromOsName(osName: String): TacitOs? {
            val name = osName.lowercase()
            return when {
                name.startsWith("mac") || name.startsWith("darwin") -> MACOS
                name.startsWith("windows") -> WINDOWS
                name.startsWith("linux") -> LINUX
                else -> null
            }
        }

        val current: TacitOs? by lazy { fromOsName(System.getProperty("os.name").orEmpty()) }
    }
}

enum class TacitUpdatePackage { DMG, ZIP }

data class TacitUpdateAsset(val name: String, val packageType: TacitUpdatePackage)

object TacitUpdateAssets {
    /** The version-less release asset (see release.yml) for this OS and architecture, or `null` if there is none. */
    fun forPlatform(os: TacitOs?, osArch: String): TacitUpdateAsset? {
        val arch = osArch.lowercase()
        val isArm64 = arch == "aarch64" || arch == "arm64"
        val isX64 = arch == "amd64" || arch == "x86_64" || arch == "x64"
        return when {
            os == TacitOs.MACOS && isArm64 -> TacitUpdateAsset("tacit-macos-arm64.dmg", TacitUpdatePackage.DMG)
            os == TacitOs.WINDOWS && isX64 -> TacitUpdateAsset("tacit-windows-x64.zip", TacitUpdatePackage.ZIP)
            os == TacitOs.LINUX && isX64 -> TacitUpdateAsset("tacit-linux-x64.zip", TacitUpdatePackage.ZIP)
            else -> null
        }
    }
}

object TacitSha256Sums {
    private val line = Regex("""^([0-9a-fA-F]{64})\s+\*?(.+)$""")

    /** Parses `sha256sum` output into file name (without directory) to lower case hex digest. */
    fun parse(text: String): Map<String, String> =
        text.lineSequence()
            .map { it.trim() }
            .mapNotNull { line.matchEntire(it) }
            .associate { match ->
                val (hash, path) = match.destructured
                path.trim().substringAfterLast('/') to hash.lowercase()
            }
}
