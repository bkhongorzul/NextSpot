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
 * Listens for the driver's spoken answer ("네" / "아니요") using Android speech recognition.
 * If speech recognition or the microphone permission isn't available, `listen` calls
 * onUnavailable and the screen falls back to the Yes / No buttons.
 */
class VoiceAnswer(private val context: Context) {

    enum class Answer { YES, NO, UNKNOWN }

    private var recognizer: SpeechRecognizer? = null

    val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context) &&
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun listen(onAnswer: (Answer, String) -> Unit, onUnavailable: () -> Unit) {
        if (!available) { onUnavailable(); return }
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val text = heard.firstOrNull().orEmpty()
                onAnswer(classify(heard), text)
                stop()
            }
            override fun onError(error: Int) { onAnswer(Answer.UNKNOWN, ""); stop() }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        private val YES = listOf("네", "예", "응", "어", "좋아", "가자", "갈게", "yes", "yeah", "ok", "okay")
        private val NO = listOf("아니", "아뇨", "싫어", "괜찮아", "no", "nope")

        fun classify(heard: List<String>): Answer {
            for (h in heard) {
                val t = h.lowercase().trim()
                if (NO.any { t.contains(it) }) return Answer.NO
                if (YES.any { t.contains(it) }) return Answer.YES
            }
            return Answer.UNKNOWN
        }
    }
}
