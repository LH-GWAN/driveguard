package kr.driveguard.core

import kotlin.math.abs

/**
 * 차량 값에서 조작으로 볼 변화를 고른다. 처음 받은 값은 기준일 뿐 조작이 아니다.
 * 조향 콜백은 조작이 없어도 같은 값으로 약 3초마다 다시 오므로 값의 차이로만 판단한다.
 */
class ControlTracker {
    private var steering: Float? = null
    private var brake: Boolean? = null
    private var turn: String? = null
    private var gear: Int? = null
    private var speed: Float? = null

    fun onSteering(value: Float?): String? {
        val v = value ?: return null
        val last = steering
        if (last != null && abs(v - last) < STEERING_DEGREES) return null
        steering = v
        return if (last == null) null else "steering"
    }

    fun onBrake(value: Boolean?): String? {
        val v = value ?: return null
        val last = brake
        brake = v
        return if (last == null || last == v) null else "brake"
    }

    fun onTurnSignal(value: String?): String? {
        val v = value ?: return null
        val last = turn
        turn = v
        return if (last == null || last == v) null else "turn"
    }

    fun onGear(value: Int?): String? {
        val v = value ?: return null
        val last = gear
        gear = v
        return if (last == null || last == v) null else RescueStateMachine.CONTROL_GEAR
    }

    fun onSpeed(value: Float?): String? {
        val v = value ?: return null
        val last = speed
        if (last != null && abs(v - last) < SPEED_MPS) return null
        speed = v
        return if (last == null) null else "speed"
    }

    companion object {
        const val STEERING_DEGREES = 1.0f
        const val SPEED_MPS = 0.1f

        fun isStopped(speedMps: Float?): Boolean? = speedMps?.let { abs(it) < SPEED_MPS }
    }
}
