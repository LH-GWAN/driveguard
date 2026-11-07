package kr.driveguard.guardian

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kr.driveguard.BuildConfig
import kr.driveguard.core.AlertMessage

/**
 * 보호자 전송의 가상 구현. 로컬 서버로만 보낸다. 실제 문자·푸시 경로는 정하지 않았고 시험하지 않았다.
 * 메시지 순서를 지키도록 한 스레드에서 차례로 보낸다. 재시도까지 실패하면 [onFailed]를 부른다.
 */
class GuardianClient(private val onFailed: () -> Unit) {
    private val executor = Executors.newSingleThreadExecutor()

    fun send(message: AlertMessage) {
        executor.execute {
            repeat(ATTEMPTS) { attempt ->
                if (post(message)) {
                    Log.i(TAG, "guardian sent ${message.kind.name}")
                    return@execute
                }
                if (attempt < ATTEMPTS - 1) Thread.sleep(RETRY_MILLIS)
            }
            Log.i(TAG, "guardian failed ${message.kind.name}")
            onFailed()
        }
    }

    fun release() = executor.shutdown()

    private fun post(message: AlertMessage): Boolean = runCatching {
        val connection = URL(BuildConfig.GUARDIAN_URL + "alerts").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(message.toJson().toByteArray(Charsets.UTF_8)) }
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    private companion object {
        const val TAG = "DriveGuard"
        const val ATTEMPTS = 3
        const val RETRY_MILLIS = 5_000L
        const val TIMEOUT_MILLIS = 3_000
    }
}
