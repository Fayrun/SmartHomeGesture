package com.example.smarthomegesture

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Quản lý kết nối Bluetooth với thiết bị IoT.
 * Sử dụng giao thức SPP (Serial Port Profile) – UUID chuẩn.
 */
class BluetoothController(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothController"
        // UUID chuẩn SPP – dùng để kết nối Bluetooth Classic
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private var socket: BluetoothSocket? = null
    private var connectedDevice: BluetoothDevice? = null

    /** Kiểm tra Bluetooth có khả dụng và được bật không */
    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /** Kiểm tra quyền truy cập Bluetooth */
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /** Lấy danh sách thiết bị đã ghép đôi (paired devices) */
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) return emptyList()
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Kết nối đến thiết bị Bluetooth theo địa chỉ MAC.
     * Chạy trên coroutine IO thread.
     */
    suspend fun connectToDevice(address: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) return@withContext false

        try {
            disconnect() // Ngắt kết nối cũ nếu có

            val device = bluetoothAdapter?.getRemoteDevice(address)
                ?: return@withContext false

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter.cancelDiscovery()
            socket?.connect()
            connectedDevice = device
            Log.d(TAG, "Connected to: ${device.name} [$address]")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed: ${e.message}")
            socket?.close()
            socket = null
            false
        }
    }

    /**
     * Gửi lệnh dưới dạng chuỗi đến thiết bị IoT.
     * Format: "CMD:VALUE\n"  (VD: "TOGGLE:1\n", "VOLUME:75\n")
     */
    suspend fun sendCommand(command: DeviceCommand): Boolean = withContext(Dispatchers.IO) {
        val outputStream = socket?.outputStream ?: return@withContext false

        val payload = buildCommandString(command)
        return@withContext try {
            outputStream.write(payload.toByteArray())
            outputStream.flush()
            Log.d(TAG, "Command sent: $payload")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Send failed: ${e.message}")
            false
        }
    }

    /** Ngắt kết nối Bluetooth */
    fun disconnect() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        } finally {
            socket = null
            connectedDevice = null
        }
    }

    fun isConnected(): Boolean = socket?.isConnected == true

    fun getConnectedDeviceName(): String? {
        if (!hasBluetoothPermissions()) return null
        return connectedDevice?.name
    }

    // ── Xây dựng chuỗi lệnh gửi đến Arduino/ESP32 ────────────────────────
    private fun buildCommandString(command: DeviceCommand): String {
        return when (command.action) {
            CommandAction.TOGGLE_POWER -> "TOGGLE:${if (command.value == 1) "ON" else "OFF"}\n"
            CommandAction.SET_VOLUME   -> "VOLUME:${command.value}\n"
            CommandAction.NEXT_TRACK   -> "NEXT\n"
            CommandAction.PREV_TRACK   -> "PREV\n"
        }
    }
}
