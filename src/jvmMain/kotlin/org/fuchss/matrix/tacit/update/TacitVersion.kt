package org.fuchss.matrix.tacit.update

/**
 * A Tacit release version, `<upstream major>.<minor>.<patch>-tacit.<n>`. Anything else (SNAPSHOT, local or
 * DEV builds) does not parse, which turns the updater off.
 */
data class TacitVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val tacit: Int,
) : Comparable<TacitVersion> {
    override fun compareTo(other: TacitVersion): Int =
        compareValuesBy(this, other, TacitVersion::major, TacitVersion::minor, TacitVersion::patch, TacitVersion::tacit)

    override fun toString(): String = "$major.$minor.$patch-tacit.$tacit"

    companion object {
        private val pattern = Regex("""^(\d+)\.(\d+)\.(\d+)-tacit\.(\d+)$""")

        /** Parses a version or a release tag (`v26.9.2-tacit.1`). */
        fun parse(value: String): TacitVersion? {
            val match = pattern.matchEntire(value.trim().removePrefix("v")) ?: return null
            val (major, minor, patch, tacit) = match.destructured
            return runCatching {
                TacitVersion(major.toInt(), minor.toInt(), patch.toInt(), tacit.toInt())
            }.getOrNull()
        }
    }
}
