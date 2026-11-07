package kr.driveguard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelterPickerTest {
    private val csv = """
        이름,시도,노선,방향,위도,경도
        가,경기도,A선,상행,37.45,126.80
        나,경기도,B선,하행,37.39,127.02
        잘못된,경기도,줄
        다,경기도,C선,하행,abc,127.0
    """.trimIndent()

    @Test fun `머리글과 형식이 다른 줄을 건너뛴다`() {
        assertEquals(listOf("가", "나"), ShelterPicker.parse(csv).map { it.name })
    }

    @Test fun `가장 가까운 곳을 고른다`() {
        val list = ShelterPicker.parse(csv)
        assertEquals("나", ShelterPicker.nearest(GeoPoint(37.40, 127.03), list)?.name)
        assertEquals("가", ShelterPicker.nearest(GeoPoint(37.46, 126.81), list)?.name)
    }

    @Test fun `목록이 비면 없다`() = assertNull(ShelterPicker.nearest(GeoPoint(0.0, 0.0), emptyList()))

    @Test fun `거리 계산이 대략 맞다`() {
        val km = ShelterPicker.distanceKm(GeoPoint(37.5665, 126.9780), GeoPoint(35.1796, 129.0756))
        assertTrue("$km", km in 320.0..330.0)
    }
}
