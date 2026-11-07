package kr.driveguard.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowMathTest {
    @Test fun `범위의 비율만큼 연다`() = assertEquals(2, WindowMath.target(0, 10, 20))
    @Test fun `올림한다`() = assertEquals(3, WindowMath.target(0, 10, 21))
    @Test fun `최소 한 칸은 연다`() = assertEquals(1, WindowMath.target(0, 100, 0))
    @Test fun `최댓값을 넘지 않는다`() = assertEquals(10, WindowMath.target(0, 10, 150))
    @Test fun `범위가 없으면 그대로다`() = assertEquals(5, WindowMath.target(5, 5, 50))
    @Test fun `최솟값이 0이 아니어도 맞다`() = assertEquals(12, WindowMath.target(10, 20, 20))
}
