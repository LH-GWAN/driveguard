package kr.driveguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertMessageTest {
    private val here = GeoPoint(37.5, 127.0)

    @Test fun `문구는 종류별 본문뿐이고 덧붙이는 안내 문장이 없다`() {
        assertEquals("차 안에서 안내를 종료했습니다.", AlertMessage.build(AlertKind.ENDED, here, 1L).text)
        assertEquals("운전자가 도움을 요청해 위치를 공유합니다.", AlertMessage.build(AlertKind.LOCATION_SHARE, here, 1L).text)
        for (k in AlertKind.values()) assertFalse(k.name, "자동 메시지" in AlertMessage.build(k, here, 1L).text)
    }

    @Test fun `본문 필드는 종류 위치 정차 시각 문구뿐이다`() {
        val names = AlertMessage::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(setOf("kind", "location", "stoppedAtMillis", "lastResponseAtMillis", "text"), names)
        val json = AlertMessage.build(AlertKind.NO_RESPONSE, here, 5L, 3L).toJson()
        val keys = Regex("\"(\\w+)\":").findAll(json).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("kind", "lat", "lng", "map", "stoppedAt", "lastResponseAt", "text"), keys)
    }

    @Test fun `문구에 병력이나 진단 표현이 없다`() {
        for (k in AlertKind.values()) {
            val text = AlertMessage.build(k, here, 1L).text
            for (word in listOf("심장", "가슴", "약", "병력", "질환", "위급", "의식")) assertFalse("$k $word", word in text)
        }
    }

    @Test fun `응답 없음은 119 신고 판단을 요청하고 마지막 응답 시각을 함께 보낸다`() {
        val m = AlertMessage.build(AlertKind.NO_RESPONSE, here, 5L, 3L)
        assertTrue("119에 신고해 주세요" in m.text)
        assertEquals(3L, m.lastResponseAtMillis)
        assertEquals("https://www.openstreetmap.org/?mlat=37.50000&mlon=127.00000#map=17/37.50000/127.00000", m.mapUrl)
    }

    @Test fun `마지막 응답 시각은 응답 없음에만 싣는다`() {
        for (k in AlertKind.values().filter { it != AlertKind.NO_RESPONSE }) {
            assertEquals(k.name, null, AlertMessage.build(k, here, 5L, 3L).lastResponseAtMillis)
        }
    }

    @Test fun `취소 메시지는 누가 취소했는지 단정하지 않는다`() =
        assertTrue("알 수 없습니다" in AlertMessage.build(AlertKind.CANCEL, null, null).text)

    @Test fun `위치가 없으면 필드를 빼고 문구로 알린다`() {
        val m = AlertMessage.build(AlertKind.NO_RESPONSE, null, 1L)
        assertTrue("위치를 확인하지 못했습니다" in m.text)
        assertFalse("lat" in m.toJson())
        assertFalse("map" in m.toJson())
    }

    @Test fun `JSON 문자열을 이스케이프한다`() {
        val m = AlertMessage(AlertKind.CANCEL, null, null, null, "a\"b\\c")
        assertEquals("{\"kind\":\"CANCEL\",\"text\":\"a\\\"b\\\\c\"}", m.toJson())
    }
}
