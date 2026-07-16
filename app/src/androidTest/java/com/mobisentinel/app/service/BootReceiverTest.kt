package com.mobisentinel.app.service

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootReceiverTest {
    @Test
    fun enabledMonitoringRestartsAfterBoot() {
        assertTrue(shouldRestartMonitoring(Intent.ACTION_BOOT_COMPLETED, enabled = true))
    }

    @Test
    fun enabledMonitoringRestartsAfterPackageReplacement() {
        assertTrue(shouldRestartMonitoring(Intent.ACTION_MY_PACKAGE_REPLACED, enabled = true))
    }

    @Test
    fun nullActionIsIgnored() {
        assertFalse(shouldRestartMonitoring(null, enabled = true))
    }

    @Test
    fun unrelatedActionIsIgnored() {
        assertFalse(shouldRestartMonitoring(Intent.ACTION_TIME_CHANGED, enabled = true))
    }

    @Test
    fun disabledMonitoringNeverRestarts() {
        assertFalse(shouldRestartMonitoring(Intent.ACTION_BOOT_COMPLETED, enabled = false))
    }
}
