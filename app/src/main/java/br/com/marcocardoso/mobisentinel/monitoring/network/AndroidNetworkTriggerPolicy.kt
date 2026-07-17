package br.com.marcocardoso.mobisentinel.monitoring.network

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.content.ContextCompat

internal object AndroidNetworkTriggerPolicy {
    const val AIRPLANE_RECEIVER_FLAGS = ContextCompat.RECEIVER_EXPORTED

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun shouldRegisterUserMobileDataCallback(sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.S
}
