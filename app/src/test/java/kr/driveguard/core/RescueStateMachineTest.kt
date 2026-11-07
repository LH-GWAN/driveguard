package kr.driveguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RescueStateMachineTest {
    private val quiet = 60_000L
    private val driving = VehicleStatus(stopped = false, park = false)
    private val stoppedInDrive = VehicleStatus(stopped = true, park = false)
    private val parked = VehicleStatus(stopped = true, park = true)
    private var now = 0L
    private fun machine(window: Boolean = true, glove: Boolean = false) =
        RescueStateMachine(RescueConfig(quiet, window, glove), now)

    private fun RescueStateMachine.go(e: RescueEvent) = onEvent(e, now)
    private fun RescueStateMachine.advance(ms: Long): List<Effect> {
        now += ms
        return go(RescueEvent.Tick)
    }

    private fun RescueStateMachine.toAsk1() {
        go(RescueEvent.Vehicle(stoppedInDrive, null))
        advance(quiet)
        assertEquals(RescueState.ASK1, state)
    }

    private fun RescueStateMachine.toAlerted(): List<Effect> {
        toAsk1()
        go(RescueEvent.SpeechDone)
        advance(10_000)
        assertEquals(RescueState.ASK2, state)
        go(RescueEvent.SpeechDone)
        val effects = advance(10_000)
        assertEquals(RescueState.ALERTED, state)
        return effects
    }

    private fun RescueStateMachine.toRescue(): List<Effect> {
        toAlerted()
        go(RescueEvent.SpeechDone)
        go(RescueEvent.Vehicle(parked, RescueStateMachine.CONTROL_GEAR))
        assertEquals(RescueState.UNLOCK_NOTICE, state)
        go(RescueEvent.SpeechDone)
        val effects = advance(10_000)
        assertEquals(RescueState.RESCUE, state)
        return effects
    }

    @Test fun `주행 중이거나 시간이 안 됐으면 묻지 않는다`() {
        val m = machine()
        m.go(RescueEvent.Vehicle(driving, null))
        m.advance(quiet * 2)
        assertEquals(RescueState.IDLE, m.state)
        m.go(RescueEvent.Vehicle(stoppedInDrive, "speed"))
        m.advance(quiet - 1)
        assertEquals(RescueState.IDLE, m.state)
    }

    @Test fun `기어 P로 멈춰 있으면 묻지 않는다`() {
        val m = machine()
        m.go(RescueEvent.Vehicle(parked, null))
        m.advance(quiet * 2)
        assertEquals(RescueState.IDLE, m.state)
    }

    @Test fun `차량 값을 모르면 묻지 않는다`() {
        val m = machine()
        m.go(RescueEvent.Vehicle(VehicleStatus(stopped = null, park = false), null))
        m.advance(quiet * 2)
        assertEquals(RescueState.IDLE, m.state)
        m.go(RescueEvent.Vehicle(VehicleStatus(stopped = true, park = null), null))
        m.advance(quiet * 2)
        assertEquals(RescueState.IDLE, m.state)
    }

    @Test fun `멈춘 채 조작이 없으면 처음에는 질문만 한다`() {
        val m = machine()
        m.go(RescueEvent.Vehicle(stoppedInDrive, null))
        val effects = m.advance(quiet)
        assertEquals(listOf<Effect>(Effect.Speak(Line.ASK)), effects)
    }

    @Test fun `조작이 있으면 대기 시간을 다시 센다`() {
        val m = machine()
        m.go(RescueEvent.Vehicle(stoppedInDrive, null))
        m.advance(quiet - 1)
        m.go(RescueEvent.Vehicle(stoppedInDrive, "brake"))
        m.advance(quiet - 1)
        assertEquals(RescueState.IDLE, m.state)
        m.advance(1)
        assertEquals(RescueState.ASK1, m.state)
    }

    @Test fun `같은 질문을 두 번 묻고 답이 없으면 연락과 창문과 안내를 한다`() {
        val m = machine()
        val effects = m.toAlerted()
        assertTrue(effects.any { it is Effect.Alert && it.kind == AlertKind.NO_RESPONSE })
        assertTrue(Effect.OpenWindow in effects)
        assertTrue(Effect.Speak(Line.PASSENGER) in effects)
        assertFalse(Effect.UnlockDoors in effects)
    }

    @Test fun `TTS가 끝나지 않아도 질문 단계가 멈추지 않는다`() {
        val m = machine()
        m.toAsk1()
        m.advance(25_000)
        assertEquals(RescueState.ASK2, m.state)
    }

    @Test fun `설정을 끄면 창문을 호출하지 않는다`() {
        assertFalse(Effect.OpenWindow in machine(window = false).toAlerted())
    }

    @Test fun `창문은 한 사건에 한 번만 연다`() {
        val m = machine()
        val first = m.toAlerted()
        assertEquals(1, first.count { it == Effect.OpenWindow })
        val rest = (1..10).flatMap { m.advance(5_000) }
        assertFalse(Effect.OpenWindow in rest)
    }

    @Test fun `응답 확인 전에는 기어 P여도 잠금을 풀지 않는다`() {
        val m = machine()
        m.go(RescueEvent.Vehicle(stoppedInDrive, null))
        val effects = m.go(RescueEvent.Vehicle(parked, RescueStateMachine.CONTROL_GEAR))
        assertEquals(RescueState.IDLE, m.state)
        assertFalse(Effect.UnlockDoors in effects)
        m.advance(quiet * 2)
        assertEquals(RescueState.IDLE, m.state)
    }

    @Test fun `기어 P 뒤 고지와 취소 시간이 지나야 잠금을 푼다`() {
        val m = machine()
        m.toAlerted()
        m.go(RescueEvent.SpeechDone)
        val notice = m.go(RescueEvent.Vehicle(parked, RescueStateMachine.CONTROL_GEAR))
        assertTrue(Effect.Speak(Line.UNLOCK_NOTICE, flush = true) in notice)
        assertFalse(Effect.UnlockDoors in notice)
        m.go(RescueEvent.SpeechDone)
        assertFalse(Effect.UnlockDoors in m.advance(9_999))
        val effects = m.advance(1)
        assertTrue(Effect.UnlockDoors in effects)
        assertTrue(Effect.CabinLightOn in effects)
        assertTrue(Effect.TailLampOn in effects)
        assertTrue(Effect.Speak(Line.OPEN_APP) in effects)
        assertTrue(Effect.OpenScreen in effects)
        assertFalse(Effect.GloveBoxUnlock in effects)
    }

    @Test fun `응급약이 글러브박스에 있을 때만 글러브박스를 푼다`() {
        assertTrue(Effect.GloveBoxUnlock in machine(glove = true).toRescue())
        assertFalse(Effect.GloveBoxUnlock in machine(glove = false).toRescue())
    }

    @Test fun `고지 중 괜찮다고 하면 잠금을 풀지 않고 취소 메시지를 보낸다`() {
        val m = machine()
        m.toAlerted()
        m.go(RescueEvent.Vehicle(parked, RescueStateMachine.CONTROL_GEAR))
        val effects = m.go(RescueEvent.VoiceOk)
        assertEquals(RescueState.IDLE, m.state)
        assertFalse(Effect.UnlockDoors in effects)
        assertEquals(1, effects.count { it is Effect.Alert && it.kind == AlertKind.CANCEL })
        assertFalse(Effect.UnlockDoors in m.advance(60_000))
    }

    @Test fun `고지 중 기어가 다시 빠지면 잠금을 풀지 않고 앞 단계로 돌아간다`() {
        val m = machine()
        m.toAlerted()
        m.go(RescueEvent.Vehicle(parked, RescueStateMachine.CONTROL_GEAR))
        val effects = m.go(RescueEvent.Vehicle(stoppedInDrive, RescueStateMachine.CONTROL_GEAR))
        assertEquals(RescueState.ALERTED, m.state)
        assertFalse(effects.any { it is Effect.Alert })
        assertFalse(Effect.UnlockDoors in m.advance(30_000))
    }

    @Test fun `응답 없음 뒤에는 어떤 차량 조작으로도 취소되지 않는다`() {
        for (control in listOf("steering", "brake", "turn", "gear", "speed")) {
            val m = machine()
            m.toAlerted()
            val effects = m.go(RescueEvent.Vehicle(stoppedInDrive, control))
            assertEquals(control, RescueState.ALERTED, m.state)
            assertFalse(control, effects.any { it is Effect.Alert })
            assertTrue(control, m.guardianNotified)
        }
    }

    @Test fun `브레이크를 밟고 기어 P로 넣으면 다음 단계로 간다`() {
        val m = machine()
        m.toAlerted()
        m.go(RescueEvent.Vehicle(stoppedInDrive, "brake"))
        m.go(RescueEvent.Vehicle(parked, RescueStateMachine.CONTROL_GEAR))
        assertEquals(RescueState.UNLOCK_NOTICE, m.state)
        m.go(RescueEvent.Vehicle(parked, "brake"))
        assertEquals(RescueState.UNLOCK_NOTICE, m.state)
        m.go(RescueEvent.SpeechDone)
        assertTrue(Effect.UnlockDoors in m.advance(10_000))
    }

    @Test fun `응답 없음 뒤 조작하면 취소 방법을 음성으로 알리되 자주 반복하지 않는다`() {
        val m = machine()
        m.toAlerted()
        val first = m.go(RescueEvent.Vehicle(stoppedInDrive, "brake"))
        assertTrue(Effect.Speak(Line.CANCEL_HINT) in first)
        assertTrue(Effect.StopListen in first)
        assertFalse(Effect.Speak(Line.CANCEL_HINT) in m.go(RescueEvent.Vehicle(stoppedInDrive, "brake")))
        now += 20_000
        assertTrue(Effect.Speak(Line.CANCEL_HINT) in m.go(RescueEvent.Vehicle(stoppedInDrive, "brake")))
    }

    @Test fun `응답 없음 뒤 괜찮다고 하면 취소 메시지를 보낸다`() {
        val m = machine()
        m.toAlerted()
        val effects = m.go(RescueEvent.VoiceOk)
        assertEquals(RescueState.IDLE, m.state)
        assertEquals(1, effects.count { it is Effect.Alert && it.kind == AlertKind.CANCEL })
    }

    @Test fun `화면 취소는 기어 P일 때만 받는다`() {
        val inDrive = machine()
        inDrive.toAlerted()
        assertTrue(inDrive.go(RescueEvent.UserCancel).isEmpty())
        assertEquals(RescueState.ALERTED, inDrive.state)
        val inPark = machine()
        inPark.toAlerted()
        inPark.go(RescueEvent.Vehicle(parked, RescueStateMachine.CONTROL_GEAR))
        val effects = inPark.go(RescueEvent.UserCancel)
        assertEquals(RescueState.IDLE, inPark.state)
        assertEquals(1, effects.count { it is Effect.Alert && it.kind == AlertKind.CANCEL })
    }

    @Test fun `응답 없음 뒤 차가 잠깐 구르면 흐름을 유지한다`() {
        val m = machine()
        m.toAlerted()
        m.go(RescueEvent.Vehicle(driving, "speed"))
        m.advance(2_999)
        m.go(RescueEvent.Vehicle(stoppedInDrive, "speed"))
        m.advance(10_000)
        assertEquals(RescueState.ALERTED, m.state)
    }

    @Test fun `응답 없음 뒤 차가 계속 움직이면 흐름을 멈추고 알리되 취소로 보내지 않는다`() {
        val m = machine()
        m.toAlerted()
        m.go(RescueEvent.Vehicle(driving, "speed"))
        val effects = m.advance(3_000)
        assertEquals(RescueState.IDLE, m.state)
        assertTrue(Effect.Speak(Line.MOVING, flush = true) in effects)
        assertEquals(1, effects.count { it is Effect.Alert && it.kind == AlertKind.MOVING })
        assertFalse(effects.any { it is Effect.Alert && it.kind == AlertKind.CANCEL })
        assertTrue(m.guardianNotified)
    }

    @Test fun `움직인 뒤 기어 P로 세우면 다시 묻기 전에는 잠금을 풀지 않는다`() {
        val m = machine()
        m.toAlerted()
        m.go(RescueEvent.Vehicle(driving, "speed"))
        m.advance(3_000)
        m.go(RescueEvent.Vehicle(parked, RescueStateMachine.CONTROL_GEAR))
        assertEquals(RescueState.IDLE, m.state)
        assertFalse(Effect.UnlockDoors in m.advance(120_000))
    }

    @Test fun `움직인 뒤 다시 멈추고 답이 없으면 처음부터 묻고 창문은 다시 열지 않는다`() {
        val m = machine()
        m.toAlerted()
        m.go(RescueEvent.Vehicle(driving, "speed"))
        m.advance(3_000)
        m.go(RescueEvent.Vehicle(stoppedInDrive, "speed"))
        m.advance(quiet)
        assertEquals(RescueState.ASK1, m.state)
        m.go(RescueEvent.SpeechDone)
        m.advance(10_000)
        m.go(RescueEvent.SpeechDone)
        val effects = m.advance(10_000)
        assertEquals(RescueState.ALERTED, m.state)
        assertFalse(Effect.OpenWindow in effects)
        assertEquals(1, effects.count { it is Effect.Alert && it.kind == AlertKind.NO_RESPONSE })
    }

    @Test fun `고지 중이나 구조 단계에서 차가 계속 움직여도 흐름을 멈춘다`() {
        val notice = machine()
        notice.toAlerted()
        notice.go(RescueEvent.Vehicle(parked, RescueStateMachine.CONTROL_GEAR))
        notice.go(RescueEvent.Vehicle(driving, "speed"))
        assertEquals(RescueState.ALERTED, notice.state)
        notice.advance(3_000)
        assertEquals(RescueState.IDLE, notice.state)
        val rescue = machine()
        rescue.toRescue()
        rescue.go(RescueEvent.Vehicle(driving, "speed"))
        rescue.advance(3_000)
        assertEquals(RescueState.IDLE, rescue.state)
    }

    @Test fun `구조 단계에서 괜찮다고 하면 취소 메시지를 보내고 끝낸다`() {
        val m = machine()
        m.toRescue()
        val effects = m.go(RescueEvent.VoiceOk)
        assertEquals(RescueState.IDLE, m.state)
        assertEquals(1, effects.count { it is Effect.Alert && it.kind == AlertKind.CANCEL })
    }

    @Test fun `질문 단계의 취소는 아무것도 보내지 않는다`() {
        for (control in listOf("steering", "brake", "turn", "gear", "speed")) {
            val m = machine()
            m.toAsk1()
            val effects = m.go(RescueEvent.Vehicle(stoppedInDrive, control))
            assertEquals(control, RescueState.IDLE, m.state)
            assertFalse(control, effects.any { it is Effect.Alert })
        }
        val m = machine()
        m.toAsk1()
        assertFalse(m.go(RescueEvent.VoiceOk).any { it is Effect.Alert })
        assertEquals(RescueState.IDLE, m.state)
    }

    @Test fun `연락 뒤 취소는 취소 메시지를 한 번 보내고 창문을 닫으라고 안내한다`() {
        val m = machine()
        m.toAlerted()
        val effects = m.go(RescueEvent.VoiceOk)
        assertEquals(RescueState.IDLE, m.state)
        assertEquals(1, effects.count { it is Effect.Alert && it.kind == AlertKind.CANCEL })
        assertTrue(Effect.Speak(Line.CLOSE_WINDOW) in effects)
        assertFalse(m.guardianNotified)
    }

    @Test fun `구조 단계는 차량 조작으로 끝나지 않는다`() {
        val m = machine()
        m.toRescue()
        m.go(RescueEvent.Vehicle(stoppedInDrive, "brake"))
        m.go(RescueEvent.Vehicle(parked, RescueStateMachine.CONTROL_GEAR))
        m.go(RescueEvent.Vehicle(parked, "steering"))
        m.go(RescueEvent.UserCancel)
        assertEquals(RescueState.RESCUE, m.state)
    }

    @Test fun `화면이나 음성으로 종료하면 종료를 알리고 처음으로 돌아간다`() {
        for (end in listOf(RescueEvent.UserEnd, RescueEvent.VoiceEnd)) {
            val m = machine()
            m.toRescue()
            val effects = m.go(end)
            assertEquals(RescueState.IDLE, m.state)
            assertEquals(1, effects.count { it is Effect.Alert && it.kind == AlertKind.ENDED })
            assertFalse(m.guardianNotified)
        }
    }

    @Test fun `구조 단계가 아니면 종료 명령은 무시한다`() {
        val m = machine()
        m.toAlerted()
        assertTrue(m.go(RescueEvent.UserEnd).isEmpty())
        assertEquals(RescueState.ALERTED, m.state)
    }

    @Test fun `도움 요청은 안내와 내비를 먼저 하고 취소가 없으면 위치를 보낸다`() {
        val m = machine()
        m.go(RescueEvent.Vehicle(driving, null))
        val effects = m.go(RescueEvent.VoiceHelp)
        assertEquals(RescueState.REQUESTED, m.state)
        assertTrue(Effect.NavigateShelter in effects)
        assertFalse(effects.any { it is Effect.Alert })
        m.go(RescueEvent.SpeechDone)
        val sent = m.advance(10_000)
        assertEquals(1, sent.count { it is Effect.Alert && it.kind == AlertKind.LOCATION_SHARE })
        assertEquals(RescueState.IDLE, m.state)
        assertTrue(m.guardianNotified)
    }

    @Test fun `도움 요청 대기 중 괜찮다고 하면 아무것도 보내지 않는다`() {
        val m = machine()
        m.go(RescueEvent.VoiceHelp)
        val effects = m.go(RescueEvent.VoiceOk)
        assertEquals(RescueState.IDLE, m.state)
        assertFalse(effects.any { it is Effect.Alert })
        assertFalse(m.advance(60_000).any { it is Effect.Alert })
    }

    @Test fun `도움 요청 뒤 주행 조작은 요청을 취소하지 않는다`() {
        val m = machine()
        m.go(RescueEvent.VoiceHelp)
        m.go(RescueEvent.Vehicle(driving, "steering"))
        m.go(RescueEvent.Vehicle(driving, "speed"))
        assertEquals(RescueState.REQUESTED, m.state)
    }

    @Test fun `도움 요청 뒤 멈추고 답이 없으면 같은 확인을 이어 간다`() {
        val m = machine()
        m.go(RescueEvent.Vehicle(driving, null))
        m.go(RescueEvent.VoiceHelp)
        m.go(RescueEvent.SpeechDone)
        m.advance(10_000)
        m.go(RescueEvent.Vehicle(stoppedInDrive, "speed"))
        m.advance(quiet)
        assertEquals(RescueState.ASK1, m.state)
        val cancel = m.go(RescueEvent.VoiceOk)
        assertEquals(1, cancel.count { it is Effect.Alert && it.kind == AlertKind.CANCEL })
    }

    @Test fun `질문 중 도움을 요청하면 요청 흐름으로 간다`() {
        val m = machine()
        m.toAsk1()
        m.go(RescueEvent.VoiceHelp)
        assertEquals(RescueState.REQUESTED, m.state)
    }

    @Test fun `정차 시각은 멈춘 순간이고 다시 움직이면 지운다`() {
        val m = machine()
        m.go(RescueEvent.Vehicle(driving, null))
        now = 5_000
        m.go(RescueEvent.Vehicle(stoppedInDrive, "speed"))
        assertEquals(5_000L, m.stoppedAtMillis)
        now = 6_000
        m.go(RescueEvent.Vehicle(stoppedInDrive, null))
        assertEquals(5_000L, m.stoppedAtMillis)
        m.go(RescueEvent.Vehicle(driving, "speed"))
        assertEquals(null, m.stoppedAtMillis)
    }
}
