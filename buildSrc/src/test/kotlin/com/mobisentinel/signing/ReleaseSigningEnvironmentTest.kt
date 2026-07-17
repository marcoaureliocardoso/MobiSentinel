package com.mobisentinel.signing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseSigningEnvironmentTest {
    private val complete = mapOf(
        "ANDROID_SIGNING_STORE_FILE" to "C:/tmp/release.p12",
        "ANDROID_SIGNING_STORE_PASSWORD" to "store-secret",
        "ANDROID_SIGNING_KEY_ALIAS" to "mobisentinel",
        "ANDROID_SIGNING_KEY_PASSWORD" to "key-secret",
    )

    @Test
    fun `returns credentials when all values exist`() {
        val result = ReleaseSigningEnvironment.resolve(complete, listOf("assembleRelease"))

        assertEquals("C:/tmp/release.p12", result?.storeFile)
        assertEquals("store-secret", result?.storePassword)
        assertEquals("mobisentinel", result?.keyAlias)
        assertEquals("key-secret", result?.keyPassword)
    }

    @Test
    fun `allows unsigned non artifact release tasks`() {
        assertNull(
            ReleaseSigningEnvironment.resolve(
                environment = emptyMap(),
                taskNames = listOf("testReleaseUnitTest", "lintRelease"),
            ),
        )
    }

    @Test
    fun `rejects release artifact without credentials`() {
        val error = assertFailsWith<IllegalStateException> {
            ReleaseSigningEnvironment.resolve(emptyMap(), listOf(":app:assembleRelease"))
        }

        assertTrue(error.message.orEmpty().contains("ANDROID_SIGNING_STORE_FILE"))
    }

    @Test
    fun `rejects partial configuration without exposing values`() {
        val error = assertFailsWith<IllegalStateException> {
            ReleaseSigningEnvironment.resolve(
                environment = mapOf(
                    "ANDROID_SIGNING_STORE_PASSWORD" to "must-not-appear",
                ),
                taskNames = listOf("assembleDebug"),
            )
        }

        assertTrue(error.message.orEmpty().contains("ANDROID_SIGNING_KEY_ALIAS"))
        assertTrue(!error.message.orEmpty().contains("must-not-appear"))
    }

    @Test
    fun `treats blank values as missing`() {
        val error = assertFailsWith<IllegalStateException> {
            ReleaseSigningEnvironment.resolve(
                environment = complete + ("ANDROID_SIGNING_KEY_ALIAS" to "  "),
                taskNames = listOf("assembleRelease"),
            )
        }

        assertTrue(error.message.orEmpty().contains("ANDROID_SIGNING_KEY_ALIAS"))
    }
}
