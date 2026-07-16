package com.mobisentinel.versioning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun `parses 0 1 0 and derives Android version code 1000`() {
        val version = AppVersion.parse("0.1.0")

        assertEquals("0.1.0", version.name)
        assertEquals(0, version.major)
        assertEquals(1, version.minor)
        assertEquals(0, version.patch)
        assertEquals(1_000, version.versionCode)
    }

    @Test
    fun `preserves ordering across patch minor and major`() {
        assertEquals(999, AppVersion.parse("0.0.999").versionCode)
        assertEquals(1_000, AppVersion.parse("0.1.0").versionCode)
        assertEquals(1_000_000, AppVersion.parse("1.0.0").versionCode)
        assertEquals(999_999_999, AppVersion.parse("999.999.999").versionCode)
    }

    @Test
    fun `rejects non stable or non canonical SemVer`() {
        val invalid = listOf(
            "1",
            "1.2",
            "v1.2.3",
            "1.2.3-beta",
            "1.2.3+4",
            "01.2.3",
            "1.02.3",
            "1.2.03",
            "-1.2.3",
        )

        invalid.forEach { value ->
            val error = assertFailsWith<IllegalArgumentException> {
                AppVersion.parse(value)
            }
            assertTrue(error.message.orEmpty().contains("X.Y.Z"), value)
        }
    }

    @Test
    fun `rejects components greater than 999`() {
        listOf("1000.0.0", "0.1000.0", "0.0.1000").forEach { value ->
            val error = assertFailsWith<IllegalArgumentException> {
                AppVersion.parse(value)
            }
            assertTrue(error.message.orEmpty().contains("0..999"), value)
        }
    }
}
