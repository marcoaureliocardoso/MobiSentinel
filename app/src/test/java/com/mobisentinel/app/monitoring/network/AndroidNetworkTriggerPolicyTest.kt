package com.mobisentinel.app.monitoring.network

import android.os.Build
import androidx.core.content.ContextCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNetworkTriggerPolicyTest {
    @Test
    fun userMobileDataCallbackIsRegisteredOnlyOnApi31AndNewer() {
        assertFalse(AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(26))
        assertFalse(AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(28))
        assertFalse(AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(30))
        assertTrue(
            AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(
                Build.VERSION_CODES.S,
            ),
        )
        assertTrue(AndroidNetworkTriggerPolicy.shouldRegisterUserMobileDataCallback(36))
    }

    @Test
    fun airplaneReceiverAcceptsSystemBroadcasts() {
        assertEquals(
            ContextCompat.RECEIVER_EXPORTED,
            AndroidNetworkTriggerPolicy.AIRPLANE_RECEIVER_FLAGS,
        )
    }
}
