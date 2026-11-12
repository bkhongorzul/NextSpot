package com.nextspot.app.car

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Fallback voice input (sim build): Android speech recognition for "네" / "아니요".
 * If it or the microphone permission isn't available, the screen falls back to Yes / No buttons.
 */
class AndroidVoiceInput(private val context: Context) : VoiceInput {
    override val label = "Android speech recognition"
    private var recognizer: SpeechRecognizer? = null

    override val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context) &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun listen(onAnswer: (VoiceInput.Answer, String) -> Unit, onUnavailable: () -> Unit) {
        if (!available) { onUnavailable(); return }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                onAnswer(VoiceInput.classify(heard), heard.firstOrNull().orEmpty())
                stop()
            }
            override fun onError(error: Int) { onAnswer(VoiceInput.Answer.UNKNOWN, ""); stop() }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        })
    }

    override fun stop() {
        recognizer?.destroy()
        recognizer = null
    }
}
