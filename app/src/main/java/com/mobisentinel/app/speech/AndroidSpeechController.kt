package com.mobisentinel.app.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidSpeechController(
    context: Context,
    private val mutableAvailability: MutableStateFlow<SpeechAvailability> =
        MutableStateFlow(SpeechAvailability.INITIALIZING),
) : SpeechController {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queue = AnnouncementQueue()
    override val availability: StateFlow<SpeechAvailability> = mutableAvailability.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var closed = false
    private var nextUtteranceId = 0L
    private var activeAnnouncementId: String? = null

    init {
        mutableAvailability.value = SpeechAvailability.INITIALIZING
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post { finishInitialization(status) }
        }
    }

    override fun announce(announcement: Announcement) {
        if (closed || availability.value != SpeechAvailability.READY) return

        mainHandler.post {
            if (closed || availability.value != SpeechAvailability.READY) return@post
            queue.offer(announcement)?.let(::speakAnnouncement)
        }
    }

    override fun testVoice() {
        if (closed || availability.value != SpeechAvailability.READY) return

        mainHandler.post {
            if (closed || availability.value != SpeechAvailability.READY) return@post
            queue.clear()
            activeAnnouncementId = null
            textToSpeech?.speak(
                "Teste de voz do MobiSentinel.",
                TextToSpeech.QUEUE_FLUSH,
                null,
                newUtteranceId("test"),
            )
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        mutableAvailability.value = SpeechAvailability.UNAVAILABLE
        mainHandler.post {
            queue.clear()
            activeAnnouncementId = null
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
        }
    }

    private fun finishInitialization(status: Int) {
        if (closed) return
        val engine = textToSpeech ?: return
        if (status != TextToSpeech.SUCCESS) {
            mutableAvailability.value = SpeechAvailability.UNAVAILABLE
            return
        }

        val languageResult = engine.setLanguage(Locale.forLanguageTag("pt-BR"))
        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            mutableAvailability.value = SpeechAvailability.UNAVAILABLE
            return
        }

        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) {
                    completeAnnouncement(utteranceId)
                }

                @Deprecated("Deprecated by Android")
                override fun onError(utteranceId: String) {
                    completeAnnouncement(utteranceId)
                }

                override fun onError(utteranceId: String, errorCode: Int) {
                    completeAnnouncement(utteranceId)
                }
            },
        )
        mutableAvailability.value = SpeechAvailability.READY
    }

    private fun speakAnnouncement(announcement: Announcement) {
        val utteranceId = newUtteranceId("announcement")
        activeAnnouncementId = utteranceId
        val result = textToSpeech?.speak(
            announcement.text,
            TextToSpeech.QUEUE_ADD,
            null,
            utteranceId,
        ) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) {
            completeAnnouncement(utteranceId)
        }
    }

    private fun completeAnnouncement(utteranceId: String) {
        mainHandler.post {
            if (utteranceId != activeAnnouncementId) return@post
            activeAnnouncementId = null
            queue.completeCurrent()?.let(::speakAnnouncement)
        }
    }

    private fun newUtteranceId(prefix: String): String = "$prefix-${nextUtteranceId++}"
}
