package kr.driveguard

import android.app.Application
import kr.driveguard.data.SecureStore
import kr.driveguard.data.Settings
import kr.driveguard.service.RescueHub

class App : Application() {
    lateinit var store: SecureStore
        private set
    lateinit var settings: Settings
        private set
    val hub = RescueHub()

    override fun onCreate() {
        super.onCreate()
        store = SecureStore(this)
        settings = Settings(this)
    }
}
