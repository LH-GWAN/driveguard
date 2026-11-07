package kr.driveguard.core

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 보호자에게 보내는 내용 전체. 위치와 정차 시각과 고정 문구뿐이다.
 * 사전 입력 정보를 받는 인자가 없어 병력·복용약이 들어갈 경로 자체가 없다.
 */
data class AlertMessage(
    val kind: AlertKind,
    val location: GeoPoint?,
    val stoppedAtMillis: Long?,
    val text: String,
) {
    fun toJson(): String = buildString {
        append("{\"kind\":\"").append(kind.name).append('"')
        location?.let { append(",\"lat\":").append(it.latitude).append(",\"lng\":").append(it.longitude) }
        stoppedAtMillis?.let { append(",\"stoppedAt\":").append(it) }
        append(",\"text\":\"").append(text.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"}")
    }

    companion object {
        const val NOTICE = "자동 메시지이며 진단이 아닙니다."

        fun build(kind: AlertKind, location: GeoPoint?, stoppedAtMillis: Long?): AlertMessage {
            val body = when (kind) {
                AlertKind.LOCATION_SHARE -> "운전자가 도움을 요청해 위치를 공유합니다."
                AlertKind.NO_RESPONSE -> "차가 멈춘 뒤 응답 확인에 답이 없었습니다."
                AlertKind.MOVING -> "응답 확인에 답이 없던 차가 다시 움직이고 있습니다. 운전자가 응답한 것인지는 알 수 없습니다."
                AlertKind.CANCEL -> "앞서 보낸 알림을 취소합니다. 차에서 응답이 있었습니다."
                AlertKind.ENDED -> "차 안에서 안내를 종료했습니다."
            }
            val place = if (location == null && kind in LOCATED) " 위치를 확인하지 못했습니다." else ""
            return AlertMessage(kind, location, stoppedAtMillis, "$body$place $NOTICE")
        }

        val LOCATED = setOf(AlertKind.LOCATION_SHARE, AlertKind.NO_RESPONSE, AlertKind.MOVING)
    }
}
