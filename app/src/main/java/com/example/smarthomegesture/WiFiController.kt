package com.example.smarthomegesture

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Điều khiển thiết bị IoT qua WiFi bằng HTTP REST API.
 * Tương thích với các module ESP8266/ESP32 chạy web server đơn giản.
 *
 * Ví dụ endpoint ESP8266:
 *   GET http://192.168.1.x/toggle  → bật/tắt relay
 *   GET http://192.168.1.x/volume?v=70
 *   GET http://192.168.1.x/next
 *   GET http://192.168.1.x/prev
 */
class WiFiController {

    companion object {
        private const val TAG     = "WiFiController"
        private const val TIMEOUT = 3000 // ms
    }

    /**
     * Gửi lệnh điều khiển đến địa chỉ IP của thiết bị IoT qua HTTP GET.
     * @param ipAddress  Địa chỉ IP của ESP8266/ESP32 (VD: "192.168.1.100")
     * @param command    Lệnh cần gửi
     * @return true nếu thành công (HTTP 200)
     */
    suspend fun sendCommand(ipAddress: String, command: DeviceCommand): Boolean =
        withContext(Dispatchers.IO) {
            val endpoint = buildEndpoint(ipAddress, command)
            return@withContext try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod      = "GET"
                    connectTimeout     = TIMEOUT
                    readTimeout        = TIMEOUT
                    instanceFollowRedirects = true
                }
                conn.connect()
                val code = conn.responseCode
                val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                conn.disconnect()
                Log.d(TAG, "[$endpoint] → $code | $body")
                code == HttpURLConnection.HTTP_OK
            } catch (e: Exception) {
                Log.e(TAG, "WiFi send error: ${e.message}")
                false
            }
        }

    /**
     * Ping thiết bị để kiểm tra kết nối.
     */
    suspend fun ping(ipAddress: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url  = URL("http://$ipAddress/ping")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout    = 2000
            conn.connect()
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) { false }
    }

    // ── Builder URL ───────────────────────────────────────────────────────
    private fun buildEndpoint(ip: String, cmd: DeviceCommand): String {
        val base = "http://$ip"
        return when (cmd.action) {
            CommandAction.TOGGLE_POWER -> "$base/toggle"
            CommandAction.SET_VOLUME   -> "$base/volume?v=${cmd.value}"
            CommandAction.NEXT_TRACK   -> "$base/next"
            CommandAction.PREV_TRACK   -> "$base/prev"
        }
    }
}
