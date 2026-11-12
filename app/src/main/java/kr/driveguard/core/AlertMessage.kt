package kr.driveguard.core

import java.util.Locale

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 보호자에게 보내는 내용 전체. 위치, 지도 링크, 정차 시각, 마지막 응답 시각, 종류별 고정 문구뿐이다.
 * 사전 입력 정보를 받는 인자가 없어 병력·복용약이 들어갈 경로 자체가 없다. 앱은 119에 직접 연결하지 않고,
 * 응답이 없을 때 보호자에게 신고를 판단해 달라고 요청한다.
 */
data class AlertMessage(
    val kind: AlertKind,
    val location: GeoPoint?,
    val stoppedAtMillis: Long?,
    val lastResponseAtMillis: Long?,
    val text: String,
) {
    val mapUrl: String? get() = location?.let { mapLink(it) }

    fun toJson(): String = buildString {
        append("{\"kind\":\"").append(kind.name).append('"')
        location?.let { append(",\"lat\":").append(it.latitude).append(",\"lng\":").append(it.longitude) }
        mapUrl?.let { append(",\"map\":\"").append(it).append('"') }
        stoppedAtMillis?.let { append(",\"stoppedAt\":").append(it) }
        lastResponseAtMillis?.let { append(",\"lastResponseAt\":").append(it) }
        append(",\"text\":\"").append(text.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"}")
    }

    companion object {
        fun build(kind: AlertKind, location: GeoPoint?, stoppedAtMillis: Long?, lastResponseAtMillis: Long? = null): AlertMessage {
            val body = when (kind) {
                AlertKind.LOCATION_SHARE -> "운전자가 도움을 요청해 위치를 공유합니다."
                AlertKind.NO_RESPONSE -> "운전자가 응답하지 않습니다. 직접 확인할 수 없다면 119에 신고해 주세요."
                AlertKind.MOVING -> "응답이 없던 차가 다시 움직이고 있습니다. 운전자가 응답한 것인지는 알 수 없습니다."
                AlertKind.CANCEL -> "앞서 보낸 알림을 취소합니다. 차 안에서 취소했으며, 운전자 본인이 취소했는지는 알 수 없습니다."
                AlertKind.ENDED -> "차 안에서 안내를 종료했습니다."
            }
            val place = if (location == null && kind in LOCATED) " 위치를 확인하지 못했습니다." else ""
            val lastResponse = if (kind == AlertKind.NO_RESPONSE) lastResponseAtMillis else null
            return AlertMessage(kind, location, stoppedAtMillis, lastResponse, "$body$place")
        }

        /** 주소 변환 없이 좌표로 여는 지도 링크. */
        fun mapLink(p: GeoPoint): String {
            val lat = String.format(Locale.US, "%.5f", p.latitude)
            val lng = String.format(Locale.US, "%.5f", p.longitude)
            return "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=17/$lat/$lng"
        }

        val LOCATED = setOf(AlertKind.LOCATION_SHARE, AlertKind.NO_RESPONSE, AlertKind.MOVING)
    }
}
