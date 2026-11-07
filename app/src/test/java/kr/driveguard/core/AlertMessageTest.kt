package kr.driveguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertMessageTest {
    private val here = GeoPoint(37.5, 127.0)

    @Test fun `모든 종류에 자동 메시지 문구가 들어간다`() {
        for (k in AlertKind.values()) assertTrue(k.name, AlertMessage.NOTICE in AlertMessage.build(k, here, 1L).text)
    }

    @Test fun `본문 필드는 종류 위치 정차 시각 문구뿐이다`() {
        val names = AlertMessage::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertEquals(setOf("kind", "location", "stoppedAtMillis", "text"), names)
        val json = AlertMessage.build(AlertKind.NO_RESPONSE, here, 5L).toJson()
        val keys = Regex("\"(\\w+)\":").findAll(json).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("kind", "lat", "lng", "stoppedAt", "text"), keys)
    }

    @Test fun `문구에 병력이나 진단 표현이 없다`() {
        for (k in AlertKind.values()) {
            val text = AlertMessage.build(k, here, 1L).text
            for (word in listOf("심장", "가슴", "약", "병력", "질환", "위급")) assertFalse("$k $word", word in text)
        }
    }

    @Test fun `위치가 없으면 필드를 빼고 문구로 알린다`() {
        val m = AlertMessage.build(AlertKind.NO_RESPONSE, null, 1L)
        assertTrue("위치를 확인하지 못했습니다" in m.text)
        assertFalse("lat" in m.toJson())
    }

    @Test fun `JSON 문자열을 이스케이프한다`() {
        val m = AlertMessage(AlertKind.CANCEL, null, null, "a\"b\\c")
        assertEquals("{\"kind\":\"CANCEL\",\"text\":\"a\\\"b\\\\c\"}", m.toJson())
    }
}
