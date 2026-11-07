package kr.driveguard.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.annotation.SuppressLint
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.os.Handler
import android.os.Looper
import kr.driveguard.core.ControlTracker
import kr.driveguard.core.VehicleStatus

/**
 * 속도·기어는 AAOS 표준 속성, 조향·브레이크·방향지시등은 Vehicle SDK로 읽는다. 콜백은 메인 스레드로 모은다.
 * 값을 못 읽으면 [VehicleStatus]의 해당 값이 null로 남아 멈춘 것으로 보지 않는다.
 */
@SuppressLint("MissingPermission")
class VehicleReader(
    private val vehicle: Vehicle,
    private val car: Car?,
    private val onChange: (VehicleStatus, String?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val tracker = ControlTracker()
    private var speed: Float? = null
    private var gear: Int? = null
    private var props: CarPropertyManager? = null

    private val steering = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = post { publish(tracker.onSteering(angle)) }
        override fun onFailed(e: Exception) = Unit
    }
    private val brake = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = post { publish(tracker.onBrake(status)) }
        override fun onFailed(e: Exception) = Unit
    }
    private val turn = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = post { publish(tracker.onTurnSignal(state?.name)) }
        override fun onFailed(e: Exception) = Unit
    }
    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) = post {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> onGear(value.value as? Int)
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> onSpeed(value.value as? Float)
            }
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) = Unit
    }

    fun start() {
        runCatching { vehicle.getSteeringWheel().registerSteeringWheelAngle(steering) }
        runCatching { vehicle.getBrake().registerBrakePedalStatus(brake) }
        runCatching { vehicle.getTurnSignal().registerTurnSignalState(turn) }
        val manager = runCatching { car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager }.getOrNull() ?: return
        props = manager
        runCatching { onGear(manager.getProperty(Int::class.javaObjectType, VehiclePropertyIds.GEAR_SELECTION, 0).value) }
        runCatching { onSpeed(manager.getProperty(Float::class.javaObjectType, VehiclePropertyIds.PERF_VEHICLE_SPEED, 0).value) }
        runCatching { manager.registerCallback(propertyCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE) }
        runCatching { manager.registerCallback(propertyCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) }
    }

    fun stop() {
        runCatching { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steering) }
        runCatching { vehicle.getBrake().unregisterBrakePedalStatus(brake) }
        runCatching { vehicle.getTurnSignal().unregisterTurnSignalState(turn) }
        runCatching { props?.unregisterCallback(propertyCallback) }
    }

    fun status() = VehicleStatus(stopped = ControlTracker.isStopped(speed), park = gear?.let { it == VehicleGear.GEAR_PARK })

    private fun onGear(value: Int?) {
        val control = tracker.onGear(value)
        if (value != null) gear = value
        publish(control)
    }

    private fun onSpeed(value: Float?) {
        val control = tracker.onSpeed(value)
        if (value != null) speed = value
        publish(control)
    }

    private fun publish(control: String?) = onChange(status(), control)

    private fun post(block: () -> Unit) {
        main.post(block)
    }
}
