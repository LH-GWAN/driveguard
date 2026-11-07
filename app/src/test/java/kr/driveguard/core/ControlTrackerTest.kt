package kr.driveguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlTrackerTest {
    @Test fun `처음 값은 조작이 아니다`() {
        val t = ControlTracker()
        assertNull(t.onSteering(5f))
        assertNull(t.onBrake(true))
        assertNull(t.onTurnSignal("LEFT"))
        assertNull(t.onGear(8))
        assertNull(t.onSpeed(3f))
    }

    @Test fun `조향은 같은 값이 반복되면 조작이 아니다`() {
        val t = ControlTracker()
        t.onSteering(0f)
        repeat(7) { assertNull(t.onSteering(-0.0f)) }
        assertNull(t.onSteering(0.5f))
        assertEquals("steering", t.onSteering(-15f))
    }

    @Test fun `조향 잔떨림이 쌓여 기준을 넘으면 조작으로 본다`() {
        val t = ControlTracker()
        t.onSteering(0f)
        assertNull(t.onSteering(0.6f))
        assertEquals("steering", t.onSteering(1.2f))
    }

    @Test fun `브레이크와 방향지시등과 기어는 값이 바뀔 때만 조작이다`() {
        val t = ControlTracker()
        t.onBrake(false); t.onTurnSignal("OFF"); t.onGear(8)
        assertNull(t.onBrake(false))
        assertEquals("brake", t.onBrake(true))
        assertNull(t.onTurnSignal("OFF"))
        assertEquals("turn", t.onTurnSignal("LEFT"))
        assertNull(t.onGear(8))
        assertEquals("gear", t.onGear(4))
    }

    @Test fun `값을 받지 못하면 조작이 아니고 기준도 지우지 않는다`() {
        val t = ControlTracker()
        t.onGear(8)
        assertNull(t.onGear(null))
        assertEquals("gear", t.onGear(4))
    }

    @Test fun `속도는 작은 흔들림을 무시한다`() {
        val t = ControlTracker()
        t.onSpeed(0f)
        assertNull(t.onSpeed(0.05f))
        assertEquals("speed", t.onSpeed(5.5f))
    }

    @Test fun `속도를 모르면 멈춘 것으로 보지 않는다`() {
        assertNull(ControlTracker.isStopped(null))
        assertEquals(true, ControlTracker.isStopped(0f))
        assertEquals(false, ControlTracker.isStopped(8.3f))
    }
}
