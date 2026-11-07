package kr.driveguard.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import android.annotation.SuppressLint
import android.util.Log
import kr.driveguard.core.WindowMath

/**
 * 차량 제어. 실패는 정상 경로다. 기어가 P가 아니면 플랫폼이 50001(ERROR_DRIVING_RESTRICTED)로 거부하므로
 * 재시도하지 않고 기록만 한다. 잠금과 조명은 [parkedAndStopped]가 참일 때만 호출한다.
 */
@SuppressLint("MissingPermission")
class VehicleControl(private val vehicle: Vehicle, private val parkedAndStopped: () -> Boolean) {

    /** 운전석 창문 한 곳만 연다. 끼임을 피하려고 앱이 닫지 않는다. */
    fun openDriverWindow(percent: Int) = attempt("window") {
        val window = vehicle.getWindow()
        window.checkWindowPositionCapabilities(
            onComplete = { _, areas ->
                val entry = areas.entries.firstOrNull { it.key.values.contains(WindowValue.ROW_1_LEFT) }
                if (entry == null) {
                    report("window", "unsupported")
                } else {
                    val target = WindowMath.target(entry.value.minValue, entry.value.maxValue, percent)
                    window.setWindowPosition(
                        WindowArea(WindowValue.ROW_1_LEFT), target,
                        { _, v -> report("window", "ok $v") },
                        { report("window", "failed ${it.message}") },
                    )
                }
            },
            onFailed = { report("window", "failed ${it.message}") },
        )
    }

    fun unlockDoors() = guarded("door") {
        val door = vehicle.getDoor()
        door.checkDoorLockCapabilities(
            onComplete = { _, areas ->
                // 후드와 트렁크를 뺀 탑승문 영역만 푼다. 이 AVD는 탑승문 4개가 묶인 영역 하나다.
                areas.filter { area -> area.values.none { it == DoorValue.HOOD || it == DoorValue.REAR } }.forEach { area ->
                    door.setDoorLock(area, false, { _, v -> report("door", "locked=$v") }, { report("door", "failed ${it.message}") })
                }
            },
            onFailed = { report("door", "failed ${it.message}") },
        )
    }

    fun cabinLightOn() = guarded("cabin") {
        vehicle.getLight().setCabinLightSwitch(LightSwitch.ON, { report("cabin", "$it") }, { report("cabin", "failed ${it.message}") })
    }

    fun tailLampOn() = guarded("tail") {
        vehicle.getLight().setTailLampLightSwitch(LightSwitch.ON, { report("tail", "$it") }, { report("tail", "failed ${it.message}") })
    }

    fun gloveBoxUnlock() = guarded("glove") {
        vehicle.getDoor().setGloveBoxLock(false, { report("glove", "locked=$it") }, { report("glove", "failed ${it.message}") })
    }

    private fun guarded(name: String, block: () -> Unit) {
        if (!parkedAndStopped()) {
            report(name, "skipped: not parked")
            return
        }
        attempt(name, block)
    }

    private fun attempt(name: String, block: () -> Unit) {
        runCatching(block).onFailure { report(name, "failed ${it.javaClass.simpleName}") }
    }

    private fun report(name: String, result: String) {
        Log.i(TAG, "control $name $result")
    }

    private companion object {
        const val TAG = "DriveGuard"
    }
}
