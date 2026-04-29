package com.example.smarthomegesture

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Helper class xử lý dữ liệu từ Accelerometer & Gyroscope
 * để nhận diện các cử chỉ tay theo thời gian thực.
 *
 * Các cử chỉ được nhận diện:
 *  - WAVE      : Vẫy tay ngang (bật/tắt đèn)
 *  - TILT_UP   : Nghiêng lên   (tăng âm lượng)
 *  - TILT_DOWN : Nghiêng xuống (giảm âm lượng)
 *  - ROTATE_LEFT  : Xoay trái (bài trước)
 *  - ROTATE_RIGHT : Xoay phải (bài tiếp)
 */
class GestureDetectorHelper(
    private val sensorManager: SensorManager,
    private val listener: GestureListener
) : SensorEventListener {

    // ── Ngưỡng phát hiện cử chỉ ────────────────────────────────────────────
    companion object {
        private const val WAVE_THRESHOLD       = 12f   // m/s² – gia tốc ngang tối thiểu
        private const val WAVE_MIN_COUNT       = 2     // số lần đổi chiều tối thiểu
        private const val WAVE_WINDOW_MS       = 1000L // cửa sổ thời gian (ms)

        private const val TILT_THRESHOLD       = 5f    // m/s² – nghiêng dọc
        private const val TILT_COOLDOWN_MS     = 300L  // thời gian chờ giữa 2 lần tilt

        private const val GYRO_ROTATE_THRESHOLD = 2.5f // rad/s – tốc độ xoay tối thiểu
        private const val GYRO_COOLDOWN_MS      = 600L
    }

    // ── Trạng thái nội bộ ──────────────────────────────────────────────────
    // Accelerometer
    private var lastAccelX   = 0f
    private var waveCount    = 0
    private var waveStartMs  = 0L
    private var lastTiltMs   = 0L

    // Gyroscope
    private var lastGyroMs   = 0L

    // Sensor references
    private val accelSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor:  Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // ── Vòng đời ──────────────────────────────────────────────────────────

    fun start() {
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun hasGyroscope(): Boolean = gyroSensor != null

    // ── SensorEventListener ────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> processAccelerometer(event)
            Sensor.TYPE_GYROSCOPE     -> processGyroscope(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* ignored */ }

    // ── Xử lý Accelerometer ───────────────────────────────────────────────

    private fun processAccelerometer(event: SensorEvent) {
        val x = event.values[0]   // Trục ngang (wave)
        val y = event.values[1]   // Trục dọc  (tilt)
        val now = System.currentTimeMillis()

        // Tính vector gia tốc thực (loại bỏ gravity trên z)
        val accelMagnitude = sqrt(x * x + y * y + event.values[2] * event.values[2])

        // --- Phát hiện WAVE (vẫy tay ngang) ---
        if (abs(x) > WAVE_THRESHOLD) {
            if (waveCount == 0) {
                waveStartMs = now
                waveCount++
                lastAccelX = x
            } else {
                val timeSinceStart = now - waveStartMs
                // Kiểm tra đổi chiều
                if ((x > 0 && lastAccelX < 0) || (x < 0 && lastAccelX > 0)) {
                    waveCount++
                    lastAccelX = x
                }
                if (timeSinceStart > WAVE_WINDOW_MS) {
                    // Reset nếu quá thời gian
                    waveCount = 0
                } else if (waveCount >= WAVE_MIN_COUNT) {
                    listener.onWaveDetected()
                    waveCount = 0
                }
            }
        }

        // --- Phát hiện TILT (nghiêng dọc) ---
        if (now - lastTiltMs > TILT_COOLDOWN_MS) {
            when {
                y < -TILT_THRESHOLD -> {
                    // Nghiêng về phía trước → tăng âm lượng
                    listener.onTiltDetected(TiltDirection.UP)
                    lastTiltMs = now
                }
                y > TILT_THRESHOLD -> {
                    // Nghiêng về phía sau → giảm âm lượng
                    listener.onTiltDetected(TiltDirection.DOWN)
                    lastTiltMs = now
                }
            }
        }

        // Gửi dữ liệu raw để hiển thị trên UI
        listener.onSensorDataUpdated(x, y, event.values[2], accelMagnitude)
    }

    // ── Xử lý Gyroscope ───────────────────────────────────────────────────

    private fun processGyroscope(event: SensorEvent) {
        val now = System.currentTimeMillis()
        if (now - lastGyroMs < GYRO_COOLDOWN_MS) return

        val rotZ = event.values[2]   // Xoay quanh trục Z (yaw)

        when {
            rotZ > GYRO_ROTATE_THRESHOLD -> {
                listener.onRotateDetected(RotateDirection.LEFT)
                lastGyroMs = now
            }
            rotZ < -GYRO_ROTATE_THRESHOLD -> {
                listener.onRotateDetected(RotateDirection.RIGHT)
                lastGyroMs = now
            }
        }

        // Gửi dữ liệu gyroscope raw
        listener.onGyroDataUpdated(event.values[0], event.values[1], rotZ)
    }
}

// ── Enum hướng ────────────────────────────────────────────────────────────

enum class TiltDirection { UP, DOWN }
enum class RotateDirection { LEFT, RIGHT }

// ── Callback interface ────────────────────────────────────────────────────

interface GestureListener {
    /** Vẫy tay → bật/tắt đèn */
    fun onWaveDetected()

    /** Nghiêng điện thoại → điều chỉnh âm lượng */
    fun onTiltDetected(direction: TiltDirection)

    /** Xoay điện thoại trái/phải → chuyển bài */
    fun onRotateDetected(direction: RotateDirection)

    /** Dữ liệu Accelerometer raw (để hiển thị đồng hồ sensor) */
    fun onSensorDataUpdated(x: Float, y: Float, z: Float, magnitude: Float)

    /** Dữ liệu Gyroscope raw */
    fun onGyroDataUpdated(x: Float, y: Float, z: Float)
}
