package com.nextspot.app.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Fallback voice output using the device's text-to-speech engine.
 * Works on any emulator. Later this can be swapped for the Pleos TextToSpeech SDK.
 * `say(text) { ... }` runs the callback when the sentence has finished playing,
 * so the app can start listening for the driver's answer only after it stops talking.
 */
class AndroidSpeaker(context: Context) : Speaker, TextToSpeech.OnInitListener {
    override val label = "Android TTS"
    private var ready = false
    private var pending: Pair<String, (() -> Unit)?>? = null
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = mutableMapOf<String, () -> Unit>()
    private var counter = 0
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val korean = tts.setLanguage(Locale.KOREAN)
        if (korean == TextToSpeech.LANG_MISSING_DATA || korean == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH)
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) = finish(id)
            override fun onStop(id: String?, interrupted: Boolean) = finish(id)
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) = finish(id)
        })
        ready = true
        pending?.let { (t, cb) -> say(t, cb) }
        pending = null
    }

    private fun finish(id: String?) {
        val cb = synchronized(callbacks) { id?.let { callbacks.remove(it) } } ?: return
        main.post(cb)
    }

    /** Language of the voice ("ko" or "en"). */
    override fun setLanguage(code: String) {
        if (ready) tts.setLanguage(if (code == "en") Locale.ENGLISH else Locale.KOREAN)
    }

    override fun say(text: String, onDone: (() -> Unit)?) {
        if (!ready) {
            pending = text to onDone
            return
        }
        val id = "nextspot-${counter++}"
        if (onDone != null) synchronized(callbacks) { callbacks[id] = onDone }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    override fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
