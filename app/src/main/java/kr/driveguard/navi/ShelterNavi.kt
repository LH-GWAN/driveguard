package kr.driveguard.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import kr.driveguard.core.Shelter

/** 길안내는 내비 앱이 맡는다. 이 앱은 쉼터 좌표를 넘긴다. 쉼터 검색 API가 없어 좌표는 앱이 가진다. */
class ShelterNavi(context: Context) {
    private val helper = NaviHelper(context.applicationContext)
    private var connected = false

    private val listener = object : NaviHelperEventListener {
        override fun onError(errorCode: NaviErrorCode) {
            Log.i(TAG, "navi error ${errorCode.name}")
        }
        override fun onRouteEnded() {
            Log.i(TAG, "navi route ready")
        }
    }

    init {
        runCatching {
            helper.initialize(
                onConnected = {
                    helper.addListener(listener)
                    connected = true
                },
                onFailed = { Log.i(TAG, "navi unavailable") },
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun routeTo(shelter: Shelter): Boolean {
        if (!connected) return false
        return runCatching {
            helper.requestRoute(
                RouteInfo(
                    latitude = shelter.point.latitude,
                    longitude = shelter.point.longitude,
                    displayLongitude = null,
                    displayLatitude = null,
                    poiName = shelter.name + " 졸음쉼터",
                    poiId = "",
                    poiSubId = "0",
                    address = "",
                ),
            )
            Log.i(TAG, "navi route requested")
        }.isSuccess
    }

    fun release() {
        runCatching {
            helper.removeListener(listener)
            helper.release()
        }
    }

    private companion object {
        const val TAG = "DriveGuard"
    }
}
