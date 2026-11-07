package kr.driveguard.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Shelter(val name: String, val point: GeoPoint)

/** 직선거리로 가장 가까운 쉼터를 고른다. 도로 진행 방향과 실제 경로는 고려하지 않는다. */
object ShelterPicker {
    fun nearest(from: GeoPoint, shelters: List<Shelter>): Shelter? =
        shelters.minByOrNull { distanceKm(from, it.point) }

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(h))
    }

    /** 이름,시도,노선,방향,위도,경도 형식. 형식이 다른 줄은 건너뛴다. */
    fun parse(csv: String): List<Shelter> = csv.lineSequence().mapNotNull { line ->
        val cols = line.split(',')
        if (cols.size < 6) return@mapNotNull null
        val lat = cols[4].trim().toDoubleOrNull() ?: return@mapNotNull null
        val lng = cols[5].trim().toDoubleOrNull() ?: return@mapNotNull null
        Shelter(cols[0].trim(), GeoPoint(lat, lng))
    }.toList()
}
