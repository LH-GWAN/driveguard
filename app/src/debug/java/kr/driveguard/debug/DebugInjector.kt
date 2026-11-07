package kr.driveguard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import kr.driveguard.voice.VoiceListener

/**
 * debug 빌드 전용. 마이크 대신 합성 음성 파일을 인식 엔진에 넣는다. 인식 엔진은 실제이고 입력만 대체한다.
 * adb shell am broadcast --user 10 -a kr.driveguard.debug.SAY -p kr.driveguard --es file help.pcm
 * 상태를 직접 바꾸는 명령은 두지 않는다. 흐름은 실제로 돌아야 한다.
 */
object DebugInjector {
    private const val ACTION = "kr.driveguard.debug.SAY"
    private var receiver: BroadcastReceiver? = null

    fun install(context: Context, listener: VoiceListener) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val file = intent.getStringExtra("file") ?: return
                Thread { DemoHooks.stream(c, listener, file) }.start()
            }
        }
        context.registerReceiver(r, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
        receiver = r
    }

    fun uninstall(context: Context) {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }
}

private object DemoHooks {
    private const val BYTES_PER_100_MS = 3_200

    fun stream(context: Context, listener: VoiceListener, file: String) {
        runCatching {
            val pcm = context.assets.open(file).use { it.readBytes() }
            var offset = 0
            while (offset < pcm.size) {
                val end = minOf(offset + BYTES_PER_100_MS, pcm.size)
                listener.speech.sendAudio(pcm.copyOfRange(offset, end))
                offset = end
                Thread.sleep(100)
            }
            listener.speech.completeAudioSend()
            Log.i("DriveGuard", "debug audio sent")
        }.onFailure { Log.i("DriveGuard", "debug audio failed ${it.javaClass.simpleName}") }
    }
}
