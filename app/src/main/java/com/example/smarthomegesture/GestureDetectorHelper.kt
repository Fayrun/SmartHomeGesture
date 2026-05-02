package com.example.smarthomegesture

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Helper class xử lý dữ liệu từ Accelerometer, Gyroscope
 * và Proximity Sensor để nhận diện cử chỉ tay.
 *
 * Cử chỉ được hỗ trợ:
 *  - WAVE (Proximity) : Vẫy tay TRƯỚC camera/cảm biến → bật/tắt đèn  ← MỚI
 *  - WAVE (Accel)     : Lắc điện thoại ngang → bật/tắt đèn (dự phòng)
 *  - TILT_UP/DOWN     : Nghiêng điện thoại → âm lượng
 *  - ROTATE_LEFT/RIGHT: Xoay cổ tay → chuyển bài
 */
class GestureDetectorHelper(
    private val sensorManager: SensorManager,
    private val listener: GestureListener
) : SensorEventListener {

    // ── Ngưỡng cảm biến ────────────────────────────────────────────────────
    companion object {
        // Accelerometer wave (dự phòng nếu không có proximity)
        private const val WAVE_THRESHOLD        = 12f
        private const val WAVE_MIN_COUNT        = 2
        private const val WAVE_WINDOW_MS        = 1000L

        // Tilt
        private const val TILT_THRESHOLD        = 5f
        private const val TILT_COOLDOWN_MS      = 300L

        // Gyroscope
        private const val GYRO_ROTATE_THRESHOLD = 2.5f
        private const val GYRO_COOLDOWN_MS      = 600L

        // Proximity wave: cần vẫy ít nhất N lần qua cảm biến trong T ms
        private const val PROX_WAVE_COUNT       = 2     // số lần tay che/bỏ tối thiểu
        private const val PROX_WAVE_WINDOW_MS   = 1500L // trong vòng 1.5 giây
        private const val PROX_COOLDOWN_MS      = 1000L // cooldown sau khi kích hoạt
    }

    // ── Trạng thái Accelerometer ───────────────────────────────────────────
    private var lastAccelX  = 0f
    private var waveCount   = 0
    private var waveStartMs = 0L
    private var lastTiltMs  = 0L

    // ── Trạng thái Gyroscope ───────────────────────────────────────────────
    private var lastGyroMs  = 0L

    // ── Trạng thái Proximity ───────────────────────────────────────────────
    private var proxWaveCount   = 0      // số lần tay che cảm biến
    private var proxWaveStartMs = 0L     // thời điểm bắt đầu đếm
    private var lastProxMs      = 0L     // cooldown sau khi trigger
    private var isHandNear      = false  // tay đang che cảm biến hay không

    // ── Sensor references ──────────────────────────────────────────────────
    private val accelSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor:  Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val proxSensor:  Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    // ── Vòng đời ──────────────────────────────────────────────────────────

    fun start() {
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        // Proximity dùng NORMAL để tiết kiệm pin (không cần cập nhật nhanh)
        proxSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun hasGyroscope(): Boolean  = gyroSensor  != null
    fun hasProximity(): Boolean  = proxSensor   != null

    // ── SensorEventListener ────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> processAccelerometer(event)
            Sensor.TYPE_GYROSCOPE     -> processGyroscope(event)
            Sensor.TYPE_PROXIMITY     -> processProximity(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Xử lý Proximity Sensor (vẫy tay trước điện thoại) ─────────────────

    private fun processProximity(event: SensorEvent) {
        val now      = System.currentTimeMillis()
        val maxRange = event.sensor.maximumRange
        val dist     = event.values[0]

        // Phát hiện tay đang che cảm biến (gần = near)
        val near = dist < maxRange

        // Chỉ xử lý khi trạng thái thay đổi (near ↔ far)
        if (near == isHandNear) return
        isHandNear = near

        // Chỉ đếm khi tay ĐẾN GẦN (che cảm biến), không đếm lúc rút tay
        if (!near) return

        // Bỏ qua nếu đang trong cooldown sau lần kích hoạt trước
        if (now - lastProxMs < PROX_COOLDOWN_MS) return

        // Bắt đầu cửa sổ đếm mới nếu quá lâu từ lần che trước
        if (proxWaveCount == 0 || now - proxWaveStartMs > PROX_WAVE_WINDOW_MS) {
            proxWaveCount   = 1
            proxWaveStartMs = now
        } else {
            proxWaveCount++
        }

        // Đủ số lần vẫy trong cửa sổ thời gian → kích hoạt!
        if (proxWaveCount >= PROX_WAVE_COUNT) {
            listener.onWaveDetected()
            proxWaveCount = 0
            lastProxMs    = now
        }
    }

    // ── Xử lý Accelerometer ───────────────────────────────────────────────

    private fun processAccelerometer(event: SensorEvent) {
        val x   = event.values[0]
        val y   = event.values[1]
        val z   = event.values[2]
        val now = System.currentTimeMillis()
        val mag = sqrt(x * x + y * y + z * z)

        // Wave dự phòng (lắc ngang) – chỉ dùng khi không có proximity
        if (!hasProximity()) {
            if (abs(x) > WAVE_THRESHOLD) {
                if (waveCount == 0) {
                    waveStartMs = now; waveCount = 1; lastAccelX = x
                } else if ((x > 0 && lastAccelX < 0) || (x < 0 && lastAccelX > 0)) {
                    lastAccelX = x; waveCount++
                    if (now - waveStartMs <= WAVE_WINDOW_MS && waveCount >= WAVE_MIN_COUNT) {
                        listener.onWaveDetected(); waveCount = 0
                    }
                } else if (now - waveStartMs > WAVE_WINDOW_MS) {
                    waveCount = 0
                }
            }
        }

        // Tilt (nghiêng)
        if (now - lastTiltMs > TILT_COOLDOWN_MS) {
            when {
                y < -TILT_THRESHOLD -> { listener.onTiltDetected(TiltDirection.UP);   lastTiltMs = now }
                y >  TILT_THRESHOLD -> { listener.onTiltDetected(TiltDirection.DOWN); lastTiltMs = now }
            }
        }

        listener.onSensorDataUpdated(x, y, z, mag)
    }

    // ── Xử lý Gyroscope ───────────────────────────────────────────────────

    private fun processGyroscope(event: SensorEvent) {
        val now  = System.currentTimeMillis()
        if (now - lastGyroMs < GYRO_COOLDOWN_MS) return
        val rotZ = event.values[2]
        when {
            rotZ >  GYRO_ROTATE_THRESHOLD -> { listener.onRotateDetected(RotateDirection.LEFT);  lastGyroMs = now }
            rotZ < -GYRO_ROTATE_THRESHOLD -> { listener.onRotateDetected(RotateDirection.RIGHT); lastGyroMs = now }
        }
        listener.onGyroDataUpdated(event.values[0], event.values[1], rotZ)
    }
}

// ── Enums ──────────────────────────────────────────────────────────────────
enum class TiltDirection   { UP, DOWN }
enum class RotateDirection { LEFT, RIGHT }

// ── Callback interface ─────────────────────────────────────────────────────
interface GestureListener {
    fun onWaveDetected()
    fun onTiltDetected(direction: TiltDirection)
    fun onRotateDetected(direction: RotateDirection)
    fun onSensorDataUpdated(x: Float, y: Float, z: Float, magnitude: Float)
    fun onGyroDataUpdated(x: Float, y: Float, z: Float)
}
