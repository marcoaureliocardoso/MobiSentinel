package com.mobisentinel.signing

data class ReleaseSigningEnvironment(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
) {
    companion object {
        private val required = listOf(
            "ANDROID_SIGNING_STORE_FILE",
            "ANDROID_SIGNING_STORE_PASSWORD",
            "ANDROID_SIGNING_KEY_ALIAS",
            "ANDROID_SIGNING_KEY_PASSWORD",
        )
        private val aggregateArtifactTasks = setOf(
            "assemble",
            "build",
            "bundle",
            "package",
        )

        fun resolve(
            environment: Map<String, String>,
            taskNames: List<String>,
        ): ReleaseSigningEnvironment? {
            val values = required.associateWith { environment[it].orEmpty().trim() }
            val configured = values.filterValues { it.isNotEmpty() }
            val artifactRequested = taskNames.any { task ->
                val taskName = task.substringAfterLast(':')
                taskName.lowercase() in aggregateArtifactTasks ||
                    taskName.matches(Regex("(?i)(assemble|bundle|package).*release"))
            }

            if (configured.isEmpty()) {
                if (artifactRequested) {
                    error("Release signing requires: ${required.joinToString()}")
                }
                return null
            }

            val missing = values.filterValues { it.isEmpty() }.keys
            if (missing.isNotEmpty()) {
                error(
                    "Incomplete release signing configuration. Missing: " +
                        missing.joinToString(),
                )
            }

            return ReleaseSigningEnvironment(
                storeFile = values.getValue("ANDROID_SIGNING_STORE_FILE"),
                storePassword = values.getValue("ANDROID_SIGNING_STORE_PASSWORD"),
                keyAlias = values.getValue("ANDROID_SIGNING_KEY_ALIAS"),
                keyPassword = values.getValue("ANDROID_SIGNING_KEY_PASSWORD"),
            )
        }
    }
}
