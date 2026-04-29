package com.example.smarthomegesture

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Repository quản lý danh sách thiết bị thông minh.
 * Lưu trữ cục bộ bằng SharedPreferences (JSON).
 */
class DeviceRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_devices", Context.MODE_PRIVATE)
    private val gson = Gson()

    /** Trả về danh sách thiết bị hiện có (hoặc mặc định nếu chưa có) */
    fun getDevices(): MutableList<SmartDevice> {
        val json = prefs.getString("devices", null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<SmartDevice>>() {}.type
            gson.fromJson(json, type)
        } else {
            getDefaultDevices()
        }
    }

    /** Lưu danh sách thiết bị */
    fun saveDevices(devices: List<SmartDevice>) {
        prefs.edit().putString("devices", gson.toJson(devices)).apply()
    }

    /** Cập nhật một thiết bị theo id */
    fun updateDevice(device: SmartDevice) {
        val devices = getDevices()
        val idx = devices.indexOfFirst { it.id == device.id }
        if (idx >= 0) {
            devices[idx] = device
            saveDevices(devices)
        }
    }

    /** Thêm thiết bị mới */
    fun addDevice(device: SmartDevice) {
        val devices = getDevices()
        devices.add(device)
        saveDevices(devices)
    }

    /** Xóa thiết bị */
    fun removeDevice(deviceId: String) {
        val devices = getDevices()
        devices.removeAll { it.id == deviceId }
        saveDevices(devices)
    }

    // ── Thiết bị mẫu mặc định ─────────────────────────────────────────────
    private fun getDefaultDevices(): MutableList<SmartDevice> = mutableListOf(
        SmartDevice(
            id   = "light_01",
            name = "Đèn phòng khách",
            type = DeviceType.LIGHT,
            connectionType = ConnectionType.BLUETOOTH
        ),
        SmartDevice(
            id   = "fan_01",
            name = "Quạt thông minh",
            type = DeviceType.FAN,
            connectionType = ConnectionType.WIFI,
            address = "192.168.1.101"
        ),
        SmartDevice(
            id   = "tv_01",
            name = "TV phòng ngủ",
            type = DeviceType.TV,
            connectionType = ConnectionType.WIFI,
            address = "192.168.1.102"
        ),
        SmartDevice(
            id   = "speaker_01",
            name = "Loa thông minh",
            type = DeviceType.SPEAKER,
            connectionType = ConnectionType.BLUETOOTH
        )
    )
}
