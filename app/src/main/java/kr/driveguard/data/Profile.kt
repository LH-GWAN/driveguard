package kr.driveguard.data

import org.json.JSONObject

enum class MedicinePlace { NONE, GLOVE_BOX, OTHER }

/** 운전자가 미리 입력한 정보. 차량 안에 암호화해 저장하고 앱 밖으로 보내지 않는다. */
data class Profile(
    val driver: String = "",
    val history: String = "",
    val medication: String = "",
    val medicinePlace: MedicinePlace = MedicinePlace.NONE,
    val medicineOther: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
) {
    fun toJson(): String = JSONObject()
        .put("driver", driver)
        .put("history", history)
        .put("medication", medication)
        .put("medicinePlace", medicinePlace.name)
        .put("medicineOther", medicineOther)
        .put("guardianName", guardianName)
        .put("guardianPhone", guardianPhone)
        .toString()

    companion object {
        fun fromJson(text: String): Profile {
            val o = JSONObject(text)
            return Profile(
                driver = o.optString("driver"),
                history = o.optString("history"),
                medication = o.optString("medication"),
                medicinePlace = runCatching { MedicinePlace.valueOf(o.optString("medicinePlace")) }.getOrDefault(MedicinePlace.NONE),
                medicineOther = o.optString("medicineOther"),
                guardianName = o.optString("guardianName"),
                guardianPhone = o.optString("guardianPhone"),
            )
        }
    }
}
