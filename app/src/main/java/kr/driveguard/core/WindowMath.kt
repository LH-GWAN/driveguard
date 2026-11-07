package kr.driveguard.core

import kotlin.math.ceil

object WindowMath {
    /** 차량이 알려 준 범위에서 비율만큼 연 값. 0이면 호출하지 않으므로 최소 한 칸은 연다. */
    fun target(min: Int, max: Int, percent: Int): Int {
        if (max <= min) return min
        val step = ceil((max - min) * percent.coerceIn(0, 100) / 100.0).toInt().coerceAtLeast(1)
        return (min + step).coerceAtMost(max)
    }
}
