package kr.driveguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.driveguard.App
import kr.driveguard.core.RescueState
import kr.driveguard.service.RescueService

class MainActivity : ComponentActivity() {
    private val app get() = application as App

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface {
                    val view by app.hub.view.collectAsStateWithLifecycle()
                    var route by remember {
                        mutableStateOf(
                            when {
                                !app.settings.consented -> Route.CONSENT
                                !app.store.exists() -> Route.SETUP
                                else -> Route.HOME
                            },
                        )
                    }
                    // 권한 안내 화면 다음에 갈 곳. 동의 직후면 사전 입력, 켜기 버튼에서 왔으면 메인 화면.
                    var afterPermissions by remember { mutableStateOf(Route.SETUP) }
                    val rescue = view.state == RescueState.RESCUE
                    val shown = if (rescue) Route.RESCUE else route
                    // 건강 정보가 보이는 화면만 화면 캡처를 막는다.
                    DisposableEffect(shown) {
                        secure(shown == Route.RESCUE || shown == Route.SETUP)
                        onDispose { }
                    }
                    when (shown) {
                        Route.CONSENT -> ConsentScreen {
                            app.settings.consented = true
                            afterPermissions = Route.SETUP
                            route = if (missingPermissions().isEmpty()) Route.SETUP else Route.PERMISSIONS
                        }
                        Route.PERMISSIONS -> PermissionScreen {
                            requestRuntimePermissions()
                            if (afterPermissions == Route.HOME) startForegroundService(Intent(this@MainActivity, RescueService::class.java))
                            route = afterPermissions
                        }
                        Route.SETUP -> SetupScreen(
                            app = app,
                            canGoBack = app.store.exists(),
                            onDone = { route = Route.HOME },
                        )
                        Route.HOME -> HomeScreen(
                            app = app,
                            view = view,
                            permissionsMissing = missingPermissions().isNotEmpty(),
                            onStart = {
                                if (missingPermissions().isEmpty()) {
                                    startRescueService()
                                } else {
                                    afterPermissions = Route.HOME
                                    route = Route.PERMISSIONS
                                }
                            },
                            onStop = ::stopRescueService,
                            onEdit = { route = Route.SETUP },
                            onDeleted = { route = Route.CONSENT },
                        )
                        Route.RESCUE -> RescueScreen(app = app, view = view)
                    }
                }
            }
        }
    }

    private fun secure(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun startRescueService() {
        requestRuntimePermissions()
        startForegroundService(Intent(this, RescueService::class.java))
    }

    private fun stopRescueService() {
        stopService(Intent(this, RescueService::class.java))
    }

    private fun requestRuntimePermissions() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
    }

    private fun missingPermissions() = RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private enum class Route { CONSENT, PERMISSIONS, SETUP, HOME, RESCUE }

    private companion object {
        val RUNTIME_PERMISSIONS = listOf(
            "pleos.car.permission.FUSED_LOCATION",
            "android.car.permission.CAR_SPEED",
            "android.car.permission.CAR_POWERTRAIN",
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}
