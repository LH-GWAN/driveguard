package kr.driveguard.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/** 차량 안에서 처리하는 ON_DEVICE TTS. 문구를 차례로 말하고 모두 끝나면 [onDrained]를 부른다. */
class Speaker(context: Context, private val onDrained: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var ready = false
    private var failed = false
    private var busy = false
    private val timeout = Runnable { finished("timeout") }

    private val tts = TextToSpeech(context, Mode.ON_DEVICE)
    private val listener = object : EventListener {
        override fun onReady() = Unit
        override fun onStart() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = post { finished("done") }
        override fun onStop() = Unit
        override fun onError(errorType: ErrorType) = post { finished(errorType.name) }
    }

    init {
        runCatching {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() = post {
                    ready = true
                    Log.i(TAG, "tts ready")
                    next()
                }
                override fun onFailure(errorType: ErrorType) = post {
                    failed = true
                    Log.i(TAG, "tts init failed ${errorType.name}")
                    drain()
                }
            })
            tts.addEventListener(listener)
        }.onFailure {
            failed = true
            Log.i(TAG, "tts init failed ${it.javaClass.simpleName}")
        }
    }

    fun say(text: String, flush: Boolean) {
        if (flush) {
            queue.clear()
            if (busy) {
                busy = false
                main.removeCallbacks(timeout)
                runCatching { tts.stop() }
            }
        }
        queue.addLast(text)
        if (failed) drain() else next()
    }

    fun release() {
        main.removeCallbacks(timeout)
        queue.clear()
        runCatching {
            tts.stop()
            tts.removeEventListener(listener)
            tts.release()
        }
    }

    private fun next() {
        if (!ready || busy) return
        val text = queue.removeFirstOrNull() ?: return
        busy = true
        main.postDelayed(timeout, TIMEOUT_MILLIS)
        val started = runCatching {
            tts.setLocale(Locale.KOREA)
            tts.speak(text)
        }
        if (started.isFailure) finished("speak failed")
    }

    private fun finished(result: String) {
        if (!busy) return
        busy = false
        main.removeCallbacks(timeout)
        Log.i(TAG, "tts $result")
        if (queue.isEmpty()) onDrained() else next()
    }

    private fun drain() {
        queue.clear()
        onDrained()
    }

    private fun post(block: () -> Unit) {
        main.post(block)
    }

    private companion object {
        const val TAG = "DriveGuard"
        const val TIMEOUT_MILLIS = 14_000L
    }
}
