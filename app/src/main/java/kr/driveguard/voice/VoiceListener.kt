package kr.driveguard.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import kr.driveguard.BuildConfig
import kr.driveguard.core.Keywords
import kr.driveguard.core.VoiceIntent

/**
 * 차량 안에서 처리하는 ON_DEVICE STT. 응답을 기다리는 구간에서만 마이크를 쓰고 상시로 듣지 않는다.
 * debug 빌드는 마이크를 열지 않고 시험 훅이 넣는 음성 파일만 인식한다.
 * 인식 문장은 저장하거나 기록하지 않고 호출·응답·종료만 고른다.
 */
class VoiceListener(context: Context, private val onIntent: (VoiceIntent) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var micOpen = false
    val speech = SpeechToText(context, Mode.ON_DEVICE)

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdated(stt: String, completed: Boolean) {
            val intent = Keywords.classify(stt) ?: return
            main.post { deliver(intent) }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() = Unit
        override fun onError(errorType: ErrorType) {
            Log.i(TAG, "stt error ${errorType.name}")
        }
    }

    private var lastIntent: VoiceIntent? = null
    private var lastIntentAt = 0L

    init {
        runCatching {
            speech.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching { speech.setLocale(Locale.KOREA) }
                    Log.i(TAG, "stt ready")
                }
                override fun onFailure(errorType: ErrorType) {
                    Log.i(TAG, "stt init failed ${errorType.name}")
                }
            })
            speech.addListener(listener)
        }.onFailure { Log.i(TAG, "stt init failed ${it.javaClass.simpleName}") }
    }

    fun open() {
        Log.i(TAG, "listen open")
        if (BuildConfig.DEBUG || micOpen) return
        micOpen = runCatching { speech.request() }.isSuccess
    }

    fun close() {
        Log.i(TAG, "listen close")
        if (!micOpen) return
        micOpen = false
        runCatching { speech.stop() }
    }

    fun release() {
        close()
        runCatching {
            speech.removeListener(listener)
            speech.release()
        }
    }

    /** 부분 결과와 최종 결과가 같은 뜻으로 연달아 오므로 한 번만 넘긴다. */
    private fun deliver(intent: VoiceIntent) {
        val now = System.currentTimeMillis()
        if (intent == lastIntent && now - lastIntentAt < REPEAT_MILLIS) return
        lastIntent = intent
        lastIntentAt = now
        Log.i(TAG, "voice ${intent.name}")
        onIntent(intent)
    }

    private companion object {
        const val TAG = "DriveGuard"
        const val REPEAT_MILLIS = 3_000L
    }
}
