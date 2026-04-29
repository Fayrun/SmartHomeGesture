package com.example.smarthomegesture

/**
 * Model đại diện cho một thiết bị thông minh (IoT device)
 */
data class SmartDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    var isOn: Boolean = false,
    var volume: Int = 50,       // 0–100, dùng cho thiết bị có âm lượng
    var connectionType: ConnectionType = ConnectionType.BLUETOOTH,
    var isConnected: Boolean = false,
    var address: String = ""    // Bluetooth MAC address hoặc WiFi IP
)

enum class DeviceType {
    LIGHT,      // Đèn thông minh
    FAN,        // Quạt thông minh
    TV,         // Tivi thông minh
    SPEAKER     // Loa thông minh
}

enum class ConnectionType {
    BLUETOOTH,
    WIFI
}

/**
 * Lệnh điều khiển gửi đến thiết bị IoT
 */
data class DeviceCommand(
    val deviceId: String,
    val action: CommandAction,
    val value: Int = 0          // Giá trị kèm theo (VD: mức âm lượng)
)

enum class CommandAction {
    TOGGLE_POWER,   // Bật/tắt
    SET_VOLUME,     // Đặt âm lượng
    NEXT_TRACK,     // Chuyển bài tiếp theo
    PREV_TRACK      // Chuyển bài trước đó
}
