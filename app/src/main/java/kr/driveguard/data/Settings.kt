package kr.driveguard.data

import android.content.Context

/** 건강 정보가 아닌 설정값만 둔다. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var consented: Boolean
        get() = prefs.getBoolean("consented", false)
        set(v) = prefs.edit().putBoolean("consented", v).apply()

    var quietSeconds: Int
        get() = prefs.getInt("quietSeconds", 60)
        set(v) = prefs.edit().putInt("quietSeconds", v.coerceAtLeast(MIN_QUIET_SECONDS)).apply()

    var windowEnabled: Boolean
        get() = prefs.getBoolean("windowEnabled", true)
        set(v) = prefs.edit().putBoolean("windowEnabled", v).apply()

    var windowPercent: Int
        get() = prefs.getInt("windowPercent", 20)
        set(v) = prefs.edit().putInt("windowPercent", v.coerceIn(1, MAX_WINDOW_PERCENT)).apply()

    var lastEditedAt: Long
        get() = prefs.getLong("lastEditedAt", 0L)
        set(v) = prefs.edit().putLong("lastEditedAt", v).apply()

    fun clear() = prefs.edit().clear().apply()

    companion object {
        const val MIN_QUIET_SECONDS = 10
        // 사람이 드나들 수 없는 폭의 임시 상한. 실제 차량에서 확인해 정해야 한다.
        const val MAX_WINDOW_PERCENT = 30
        const val RECONFIRM_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
