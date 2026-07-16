package com.mobisentinel.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("com.mobisentinel.app", BuildConfig.APPLICATION_ID)
    }
}
