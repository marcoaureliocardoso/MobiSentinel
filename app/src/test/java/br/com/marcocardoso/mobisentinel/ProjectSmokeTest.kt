package br.com.marcocardoso.mobisentinel

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("br.com.marcocardoso.mobisentinel", BuildConfig.APPLICATION_ID)
    }
}
