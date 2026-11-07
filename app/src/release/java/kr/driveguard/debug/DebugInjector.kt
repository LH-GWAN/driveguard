package kr.driveguard.debug

import android.content.Context
import kr.driveguard.voice.VoiceListener

/** release 빌드에는 시험 훅이 없다. */
object DebugInjector {
    @Suppress("UNUSED_PARAMETER")
    fun install(context: Context, listener: VoiceListener) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun uninstall(context: Context) = Unit
}
