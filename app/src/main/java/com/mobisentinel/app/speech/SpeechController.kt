package com.mobisentinel.app.speech

import com.mobisentinel.app.monitoring.model.Transport
import kotlinx.coroutines.flow.StateFlow

enum class SpeechAvailability { INITIALIZING, READY, UNAVAILABLE }

data class Announcement(
    val transport: Transport,
    val text: String,
)

interface SpeechController {
    val availability: StateFlow<SpeechAvailability>

    fun announce(announcement: Announcement)

    fun testVoice()

    fun close()
}
