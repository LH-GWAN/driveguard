package kr.driveguard.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.driveguard.core.RescueEvent
import kr.driveguard.core.RescueState
import kr.driveguard.core.VehicleStatus

data class RescueView(
    val running: Boolean = false,
    val state: RescueState = RescueState.IDLE,
    val vehicle: VehicleStatus = VehicleStatus(),
    val stoppedAtMillis: Long? = null,
    val lastResponseAtMillis: Long? = null,
    val guardianNotified: Boolean = false,
)

/** 서비스와 화면 사이의 연결. 건강 정보는 싣지 않는다. */
class RescueHub {
    private val _view = MutableStateFlow(RescueView())
    val view: StateFlow<RescueView> = _view.asStateFlow()

    var sink: ((RescueEvent) -> Unit)? = null

    fun publish(view: RescueView) {
        _view.value = view
    }

    fun send(event: RescueEvent) {
        sink?.invoke(event)
    }
}
