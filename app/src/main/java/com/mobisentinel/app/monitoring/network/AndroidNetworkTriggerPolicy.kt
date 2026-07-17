package com.mobisentinel.app.monitoring.network

import android.os.Build
import androidx.core.content.ContextCompat

internal object AndroidNetworkTriggerPolicy {
    const val AIRPLANE_RECEIVER_FLAGS = ContextCompat.RECEIVER_EXPORTED

    fun shouldRegisterUserMobileDataCallback(sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.S
}
