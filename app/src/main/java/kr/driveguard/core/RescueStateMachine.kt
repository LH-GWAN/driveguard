package kr.driveguard.core

enum class RescueState { IDLE, REQUESTED, ASK1, ASK2, ALERTED, UNLOCK_NOTICE, RESCUE }

enum class Line { HELP_GUIDE, ASK, PASSENGER, UNLOCK_NOTICE, OPEN_APP, CANCELLED, CLOSE_WINDOW, ENDED }

enum class AlertKind { LOCATION_SHARE, NO_RESPONSE, CANCEL, ENDED }

/** 차량 상태. 읽지 못한 값은 null이며 null이면 멈춘 것으로 보지 않는다. */
data class VehicleStatus(val stopped: Boolean? = null, val park: Boolean? = null)

data class RescueConfig(
    val quietMillis: Long,
    val windowEnabled: Boolean = true,
    val medicineInGloveBox: Boolean = false,
    val responseMillis: Long = 10_000,
    val speakTimeoutMillis: Long = 15_000,
    val requestCancelMillis: Long = 10_000,
    val unlockCancelMillis: Long = 10_000,
)

sealed interface RescueEvent {
    data object Tick : RescueEvent
    /** [control]은 조작으로 볼 변화의 이름이다. 값이 그대로면 null이다. */
    data class Vehicle(val status: VehicleStatus, val control: String?) : RescueEvent
    data object VoiceHelp : RescueEvent
    data object VoiceOk : RescueEvent
    data object VoiceEnd : RescueEvent
    data object SpeechDone : RescueEvent
    data object UserHelp : RescueEvent
    data object UserCancel : RescueEvent
    data object UserEnd : RescueEvent
}

sealed interface Effect {
    data class Speak(val line: Line, val flush: Boolean = false) : Effect
    data object Listen : Effect
    data object StopListen : Effect
    data class Alert(val kind: AlertKind, val stoppedAtMillis: Long?) : Effect
    data object NavigateShelter : Effect
    data object OpenWindow : Effect
    data object UnlockDoors : Effect
    data object CabinLightOn : Effect
    data object TailLampOn : Effect
    data object GloveBoxUnlock : Effect
    data object OpenScreen : Effect
}

/**
 * 멈춤 후 응답 확인의 흐름. Android에 의존하지 않고 입력 이벤트와 시각만으로 동작한다.
 *
 * 앱은 상태를 탐지하거나 해석하지 않는다. 멈춰 있고 조작 변화가 없으면 묻고, 답이 없으면 정해 둔 연락과
 * 차량 동작을 한다. 잠금 해제와 조명은 ALERTED를 거친 뒤 속도 0이고 기어 P일 때만 일어난다.
 */
class RescueStateMachine(private val config: RescueConfig, now: Long) {
    var state = RescueState.IDLE
        private set
    var status = VehicleStatus()
        private set
    var stoppedAtMillis: Long? = null
        private set
    var lastResponseAtMillis = now
        private set
    var guardianNotified = false
        private set

    private var deadline = 0L
    private var speaking = false
    private var windowOpened = false

    fun onEvent(event: RescueEvent, now: Long): List<Effect> = when (event) {
        RescueEvent.Tick -> onTick(now)
        is RescueEvent.Vehicle -> onVehicle(event, now)
        RescueEvent.VoiceHelp, RescueEvent.UserHelp -> onHelp(now)
        RescueEvent.VoiceOk -> onOk(now)
        RescueEvent.VoiceEnd, RescueEvent.UserEnd -> onEnd(now)
        RescueEvent.SpeechDone -> onSpeechDone(now)
        RescueEvent.UserCancel -> onUserCancel(now)
    }

    private fun onTick(now: Long): List<Effect> = when (state) {
        RescueState.IDLE ->
            if (status.stopped == true && status.park == false && now - lastResponseAtMillis >= config.quietMillis) {
                enterAsk(RescueState.ASK1, now)
            } else {
                emptyList()
            }
        RescueState.REQUESTED ->
            if (now >= deadline) {
                state = RescueState.IDLE
                guardianNotified = true
                listOf(Effect.StopListen, Effect.Alert(AlertKind.LOCATION_SHARE, stoppedAtMillis))
            } else {
                emptyList()
            }
        RescueState.ASK1 -> if (now >= deadline) listOf(Effect.StopListen) + enterAsk(RescueState.ASK2, now) else emptyList()
        RescueState.ASK2 -> if (now >= deadline) listOf(Effect.StopListen) + enterAlerted(now) else emptyList()
        RescueState.UNLOCK_NOTICE ->
            if (now < deadline) {
                emptyList()
            } else if (status.stopped == true && status.park == true) {
                listOf(Effect.StopListen) + enterRescue(now)
            } else {
                cancel(now)
            }
        RescueState.ALERTED, RescueState.RESCUE ->
            // 답을 계속 받아야 하는 단계라 듣는 구간을 이어 간다.
            if (!speaking && now >= deadline) {
                deadline = now + config.responseMillis
                listOf(Effect.StopListen, Effect.Listen)
            } else {
                emptyList()
            }
    }

    private fun onVehicle(event: RescueEvent.Vehicle, now: Long): List<Effect> {
        val wasStopped = status.stopped
        status = event.status
        if (status.stopped == true && wasStopped != true) stoppedAtMillis = now
        if (status.stopped == false) stoppedAtMillis = null
        val control = event.control ?: return emptyList()
        return when (state) {
            RescueState.IDLE -> {
                lastResponseAtMillis = now
                emptyList()
            }
            // 안전한 곳으로 가는 중이라 조작이 당연하다. 음성으로만 취소한다.
            RescueState.REQUESTED -> {
                lastResponseAtMillis = now
                emptyList()
            }
            RescueState.ASK1, RescueState.ASK2, RescueState.UNLOCK_NOTICE -> cancel(now)
            RescueState.ALERTED ->
                if (control == CONTROL_GEAR && status.park == true && status.stopped == true) {
                    enterUnlockNotice(now)
                } else {
                    cancel(now)
                }
            // 구조자가 차를 만질 수 있으므로 조작으로 끝내지 않는다.
            RescueState.RESCUE -> emptyList()
        }
    }

    private fun onHelp(now: Long): List<Effect> = when (state) {
        RescueState.IDLE, RescueState.ASK1, RescueState.ASK2 -> {
            val stop = if (state == RescueState.IDLE) emptyList() else listOf(Effect.StopListen)
            lastResponseAtMillis = now
            state = RescueState.REQUESTED
            speaking = true
            deadline = now + config.speakTimeoutMillis + config.requestCancelMillis
            stop + listOf(Effect.Speak(Line.HELP_GUIDE, flush = true), Effect.NavigateShelter)
        }
        else -> emptyList()
    }

    private fun onOk(now: Long): List<Effect> = when (state) {
        RescueState.RESCUE, RescueState.IDLE -> emptyList()
        else -> cancel(now)
    }

    private fun onEnd(now: Long): List<Effect> {
        if (state != RescueState.RESCUE) return emptyList()
        reset(now)
        return listOf(
            Effect.StopListen,
            Effect.Speak(Line.ENDED, flush = true),
            Effect.Alert(AlertKind.ENDED, null),
        )
    }

    private fun onSpeechDone(now: Long): List<Effect> {
        if (!speaking) return emptyList()
        speaking = false
        val wait = when (state) {
            RescueState.REQUESTED -> config.requestCancelMillis
            RescueState.ASK1, RescueState.ASK2, RescueState.ALERTED, RescueState.RESCUE -> config.responseMillis
            RescueState.UNLOCK_NOTICE -> config.unlockCancelMillis
            RescueState.IDLE -> return emptyList()
        }
        deadline = now + wait
        return listOf(Effect.Listen)
    }

    private fun onUserCancel(now: Long): List<Effect> =
        if (state == RescueState.RESCUE) emptyList()
        else if (state == RescueState.IDLE && !guardianNotified) emptyList()
        else cancel(now)

    private fun enterAsk(next: RescueState, now: Long): List<Effect> {
        state = next
        speaking = true
        deadline = now + config.speakTimeoutMillis + config.responseMillis
        return listOf(Effect.Speak(Line.ASK))
    }

    private fun enterAlerted(now: Long): List<Effect> {
        state = RescueState.ALERTED
        guardianNotified = true
        speaking = true
        deadline = now + config.speakTimeoutMillis
        val effects = mutableListOf<Effect>(Effect.Alert(AlertKind.NO_RESPONSE, stoppedAtMillis))
        if (config.windowEnabled && !windowOpened && status.stopped == true) {
            windowOpened = true
            effects += Effect.OpenWindow
        }
        effects += Effect.Speak(Line.PASSENGER)
        return effects
    }

    private fun enterUnlockNotice(now: Long): List<Effect> {
        state = RescueState.UNLOCK_NOTICE
        speaking = true
        deadline = now + config.speakTimeoutMillis + config.unlockCancelMillis
        return listOf(Effect.StopListen, Effect.Speak(Line.UNLOCK_NOTICE, flush = true))
    }

    private fun enterRescue(now: Long): List<Effect> {
        state = RescueState.RESCUE
        speaking = true
        deadline = now + config.speakTimeoutMillis
        val effects = mutableListOf(Effect.UnlockDoors, Effect.CabinLightOn, Effect.TailLampOn)
        if (config.medicineInGloveBox) effects += Effect.GloveBoxUnlock
        effects += Effect.Speak(Line.OPEN_APP)
        effects += Effect.OpenScreen
        return effects
    }

    private fun cancel(now: Long): List<Effect> {
        val notified = guardianNotified
        val windowWasOpened = windowOpened
        val stoppedAt = stoppedAtMillis
        reset(now)
        val effects = mutableListOf<Effect>(Effect.StopListen)
        effects += Effect.Speak(Line.CANCELLED, flush = true)
        if (windowWasOpened) effects += Effect.Speak(Line.CLOSE_WINDOW)
        if (notified) effects += Effect.Alert(AlertKind.CANCEL, stoppedAt)
        return effects
    }

    private fun reset(now: Long) {
        state = RescueState.IDLE
        lastResponseAtMillis = now
        guardianNotified = false
        windowOpened = false
        speaking = false
    }

    companion object {
        const val CONTROL_GEAR = "gear"
    }
}
