package kr.driveguard.service

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.vehicle.Vehicle
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.car.Car
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kr.driveguard.App
import kr.driveguard.R
import kr.driveguard.core.AlertMessage
import kr.driveguard.core.Effect
import kr.driveguard.core.GeoPoint
import kr.driveguard.core.Line
import kr.driveguard.core.RescueConfig
import kr.driveguard.core.RescueEvent
import kr.driveguard.core.RescueStateMachine
import kr.driveguard.core.Shelter
import kr.driveguard.core.ShelterPicker
import kr.driveguard.core.VoiceIntent
import kr.driveguard.data.MedicinePlace
import kr.driveguard.debug.DebugInjector
import kr.driveguard.guardian.GuardianClient
import kr.driveguard.navi.ShelterNavi
import kr.driveguard.ui.MainActivity
import kr.driveguard.vehicle.VehicleControl
import kr.driveguard.vehicle.VehicleReader
import kr.driveguard.voice.Speaker
import kr.driveguard.voice.VoiceListener

/**
 * 앱이 뒤에 있어도 멈춤 후 응답 확인을 돌리는 포그라운드 서비스. 일반 백그라운드 서비스는 약 1분 뒤 중단되어 쓸 수 없다.
 * 모든 이벤트는 메인 스레드에서 상태 기계로 들어간다. 로그에는 상태 이름만 남긴다.
 */
class RescueService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val hub get() = (application as App).hub

    private lateinit var machine: RescueStateMachine
    private lateinit var vehicle: Vehicle
    private lateinit var reader: VehicleReader
    private lateinit var control: VehicleControl
    private lateinit var speaker: Speaker
    private lateinit var listener: VoiceListener
    private lateinit var navi: ShelterNavi
    private lateinit var guardian: GuardianClient
    private lateinit var fused: FusedLocation
    private var car: Car? = null
    private var windowPercent = 20
    private var created = false
    private val shelters: List<Shelter> by lazy {
        runCatching { ShelterPicker.parse(assets.open("shelters.csv").bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
    }

    private val tick = object : Runnable {
        override fun run() {
            dispatch(RescueEvent.Tick)
            main.postDelayed(this, TICK_MILLIS)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val foreground = runCatching {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
        if (foreground.isFailure) {
            // 프로세스가 죽은 뒤 시스템이 다시 시작할 때 거부될 수 있다. 그때는 운전자 확인부터 다시 한다.
            Log.i(TAG, "foreground refused ${foreground.exceptionOrNull()?.javaClass?.simpleName}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!created) {
            created = true
            logLastExit()
            begin()
        }
        return START_STICKY
    }

    /** 서비스가 중간에 사라진 원인을 찾기 위한 기록. 이유 코드만 남긴다. */
    private fun logLastExit() {
        val manager = getSystemService(ActivityManager::class.java)
        val last = runCatching { manager.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull() }.getOrNull() ?: return
        Log.i(TAG, "last exit reason=${last.reason} status=${last.status}")
    }

    private fun begin() {
        val app = application as App
        val settings = app.settings
        windowPercent = settings.windowPercent
        val profile = app.store.load()
        machine = RescueStateMachine(
            RescueConfig(
                quietMillis = settings.quietSeconds * 1_000L,
                windowEnabled = settings.windowEnabled,
                medicineInGloveBox = profile?.medicinePlace == MedicinePlace.GLOVE_BOX,
            ),
            System.currentTimeMillis(),
        )
        vehicle = Vehicle(applicationContext).also { it.initialize() }
        car = runCatching { Car.createCar(applicationContext) }.getOrNull()
        fused = FusedLocation(applicationContext).also { runCatching { it.initialize() } }
        control = VehicleControl(vehicle) { machine.status.stopped == true && machine.status.park == true }
        speaker = Speaker(applicationContext) { dispatch(RescueEvent.SpeechDone) }
        listener = VoiceListener(applicationContext, ::onVoice)
        navi = ShelterNavi(applicationContext)
        guardian = GuardianClient { main.post { speaker.say(getString(R.string.say_send_failed), flush = false) } }
        reader = VehicleReader(vehicle, car) { status, reason -> dispatch(RescueEvent.Vehicle(status, reason)) }
        reader.start()
        DebugInjector.install(applicationContext, listener)
        hub.sink = { event -> main.post { dispatch(event) } }
        main.post(tick)
        Log.i(TAG, "service started")
        publish()
    }

    private fun onVoice(intent: VoiceIntent) {
        dispatch(
            when (intent) {
                VoiceIntent.HELP -> RescueEvent.VoiceHelp
                VoiceIntent.OK -> RescueEvent.VoiceOk
                VoiceIntent.CANCEL -> RescueEvent.VoiceCancel
                VoiceIntent.END -> RescueEvent.VoiceEnd
            },
        )
    }

    private fun dispatch(event: RescueEvent) {
        val before = machine.state
        val effects = machine.onEvent(event, System.currentTimeMillis())
        if (machine.state != before) Log.i(TAG, "state ${before.name} -> ${machine.state.name}")
        effects.forEach(::perform)
        publish()
    }

    private fun perform(effect: Effect) {
        when (effect) {
            is Effect.Speak -> {
                Log.i(TAG, "say ${effect.line.name}")
                speaker.say(text(effect.line), effect.flush)
            }
            Effect.Listen -> listener.open()
            Effect.StopListen -> listener.close()
            is Effect.Alert -> sendAlert(effect)
            Effect.NavigateShelter -> navigate()
            Effect.OpenWindow -> control.openDriverWindow(windowPercent)
            Effect.UnlockDoors -> control.unlockDoors()
            Effect.CabinLightOn -> control.cabinLightOn()
            Effect.TailLampOn -> control.tailLampOn()
            Effect.GloveBoxUnlock -> control.gloveBoxUnlock()
            Effect.OpenScreen -> openScreen()
        }
    }

    private fun sendAlert(effect: Effect.Alert) {
        if (effect.kind !in AlertMessage.LOCATED) {
            guardian.send(AlertMessage.build(effect.kind, null, effect.stoppedAtMillis))
            return
        }
        withLocation { point -> guardian.send(AlertMessage.build(effect.kind, point, effect.stoppedAtMillis, effect.lastResponseAtMillis)) }
    }

    private fun navigate() {
        withLocation { point ->
            val shelter = point?.let { ShelterPicker.nearest(it, shelters) }
            if (shelter == null || !navi.routeTo(shelter)) {
                speaker.say(getString(R.string.say_no_location), flush = false)
            }
        }
    }

    /** 위치를 못 받으면 정해진 시간 뒤 null로 진행한다. 위치 때문에 연락이 막히면 안 된다. */
    @SuppressLint("MissingPermission")
    private fun withLocation(block: (GeoPoint?) -> Unit) {
        var done = false
        fun finish(point: GeoPoint?) {
            if (done) return
            done = true
            block(point)
        }
        main.postDelayed({ finish(null) }, LOCATION_TIMEOUT_MILLIS)
        val requested = runCatching {
            fused.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    main.post { finish(location?.let { GeoPoint(it.latitude, it.longitude) }) }
                }
                override fun onError(exception: Exception) {
                    main.post { finish(null) }
                }
            })
        }
        if (requested.isFailure) finish(null)
    }

    /** 서비스에서 화면을 띄우는 동작은 결과가 일정하지 않아 시도만 한다. 음성 안내가 기본 경로다. */
    private fun openScreen() {
        runCatching {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun text(line: Line): String = getString(
        when (line) {
            Line.HELP_GUIDE -> R.string.say_help_guide
            Line.ASK -> R.string.say_ask
            Line.PASSENGER -> R.string.say_passenger
            Line.CANCEL_HINT -> R.string.say_cancel_hint
            Line.MOVING -> R.string.say_moving
            Line.UNLOCK_NOTICE -> R.string.say_unlock_notice
            Line.OPEN_APP -> R.string.say_open_app
            Line.CANCELLED -> R.string.say_cancelled
            Line.CLOSE_WINDOW -> R.string.say_close_window
            Line.ENDED -> R.string.say_ended
        },
    )

    private fun publish() {
        hub.publish(
            RescueView(
                running = true,
                state = machine.state,
                vehicle = machine.status,
                stoppedAtMillis = machine.stoppedAtMillis,
                lastResponseAtMillis = machine.lastResponseAtMillis,
                guardianNotified = machine.guardianNotified,
            ),
        )
    }

    private fun notification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_title))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        hub.sink = null
        hub.publish(RescueView())
        if (created) {
            DebugInjector.uninstall(applicationContext)
            reader.stop()
            speaker.release()
            listener.release()
            navi.release()
            guardian.release()
            runCatching { fused.release() }
            runCatching { vehicle.release() }
            runCatching { car?.disconnect() }
        }
        Log.i(TAG, "service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "DriveGuard"
        const val CHANNEL = "driveguard"
        const val NOTIFICATION_ID = 1
        const val TICK_MILLIS = 500L
        const val LOCATION_TIMEOUT_MILLIS = 2_000L
    }
}
