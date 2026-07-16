package com.mobisentinel.versioning

data class AppVersion private constructor(
    val name: String,
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    val versionCode: Int = major * 1_000_000 + minor * 1_000 + patch

    companion object {
        private val stableSemVer =
            Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""")

        fun parse(value: String): AppVersion {
            val match = stableSemVer.matchEntire(value)
                ?: throw IllegalArgumentException(
                    "Version '$value' must use stable SemVer X.Y.Z without prefixes or suffixes",
                )
            val components = match.groupValues.drop(1).map { component ->
                component.toLongOrNull()
                    ?: throw IllegalArgumentException("Version '$value' must use SemVer X.Y.Z")
            }
            if (components.any { it !in 0L..999L }) {
                throw IllegalArgumentException(
                    "Version '$value' must keep every component in 0..999",
                )
            }

            return AppVersion(
                name = value,
                major = components[0].toInt(),
                minor = components[1].toInt(),
                patch = components[2].toInt(),
            )
        }
    }
}
