package com.nextspot.app.car

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Voice output using the device's text-to-speech engine.
 * Works on any emulator. Later this can be swapped for the Pleos TextToSpeech SDK.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private var ready = false
    private var pending: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val korean = tts.setLanguage(Locale.KOREAN)
        if (korean == TextToSpeech.LANG_MISSING_DATA || korean == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        ready = true
        pending?.let { say(it) }
        pending = null
    }

    fun say(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nextspot")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
