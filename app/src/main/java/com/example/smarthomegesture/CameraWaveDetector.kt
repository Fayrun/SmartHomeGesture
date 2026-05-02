package com.example.smarthomegesture

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Phát hiện cử chỉ vẫy tay qua CAMERA TRƯỚC.
 *
 * Thuật toán: So sánh frame liên tiếp (Frame Differencing)
 *  1. Lấy ảnh grayscale từ camera trước
 *  2. Tính tổng pixel thay đổi giữa 2 frame liền kề
 *  3. Nếu vùng trung tâm có thay đổi lớn → phát hiện chuyển động (tay vẫy)
 *  4. Đếm sự kiện chuyển động, đủ N lần trong T giây → kích hoạt WAVE
 *
 * Ưu điểm:
 *  - Không cần thư viện ML, chạy hoàn toàn offline
 *  - Nhẹ, không tốn pin
 *  - Phù hợp mọi điện thoại Android
 */
class CameraWaveDetector(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onWaveDetected: () -> Unit
) {

    companion object {
        private const val TAG = "CameraWaveDetector"

        // Ngưỡng chuyển động – tổng pixel diff / tổng pixel phân tích
        private const val MOTION_THRESHOLD   = 0.08f  // 8% pixel thay đổi
        // Số lần chuyển động tối thiểu để trigger wave
        private const val WAVE_COUNT         = 2
        // Cửa sổ thời gian (ms)
        private const val WAVE_WINDOW_MS     = 1500L
        // Cooldown sau khi trigger (ms)
        private const val WAVE_COOLDOWN_MS   = 1200L

        // Tỷ lệ vùng phân tích (lấy vùng giữa frame, tránh background)
        private const val ANALYZE_RATIO      = 0.5f
    }

    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    // Trạng thái phát hiện
    private var prevPixels: ByteArray? = null
    private var motionCount  = 0
    private var windowStartMs = 0L
    private var lastWaveMs   = 0L
    private var isRunning    = false

    // Preview view (optional – có thể null nếu không muốn hiển thị)
    var previewView: PreviewView? = null

    // ── Khởi động camera ──────────────────────────────────────────────────

    fun start() {
        if (isRunning) return
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindCamera()
            isRunning = true
            Log.d(TAG, "Camera wave detector started")
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        cameraProvider?.unbindAll()
        isRunning = false
        prevPixels = null
        Log.d(TAG, "Camera wave detector stopped")
    }

    fun isRunning() = isRunning

    // ── Bind CameraX use cases ────────────────────────────────────────────

    private fun bindCamera() {
        val provider = cameraProvider ?: return

        // Chọn camera trước
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        // Preview (optional)
        val preview = Preview.Builder()
            .setTargetResolution(android.util.Size(320, 240))
            .build()
            .also { it.setSurfaceProvider(previewView?.surfaceProvider) }

        // Image Analysis – phân tích frame để phát hiện chuyển động
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(160, 120)) // nhỏ để xử lý nhanh
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(cameraExecutor, MotionAnalyzer()) }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
        } catch (e: Exception) {
            Log.e(TAG, "Camera bind failed: ${e.message}")
        }
    }

    // ── Analyzer: phân tích từng frame ────────────────────────────────────

    private inner class MotionAnalyzer : ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy) {
            val now = System.currentTimeMillis()

            // Trích xuất Y plane (grayscale) từ YUV_420_888
            val yPlane   = image.planes[0]
            val yBuffer: ByteBuffer = yPlane.buffer
            val yBytes   = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)

            val width    = image.width
            val height   = image.height

            // Tính motion score trong vùng trung tâm frame
            val motionScore = calcMotionScore(yBytes, width, height)

            val prev = prevPixels
            prevPixels = yBytes

            image.close()

            if (prev == null) return // frame đầu tiên, chưa so sánh được

            // Phát hiện chuyển động đủ lớn
            if (motionScore > MOTION_THRESHOLD) {
                handleMotionEvent(now)
            }
        }

        /**
         * Tính điểm chuyển động: tỷ lệ pixel thay đổi so với frame trước.
         * Chỉ phân tích vùng TRUNG TÂM của frame (loại bỏ background).
         */
        private fun calcMotionScore(current: ByteArray, width: Int, height: Int): Float {
            val prev = prevPixels ?: return 0f

            // Vùng phân tích: center crop
            val xStart = (width  * (1 - ANALYZE_RATIO) / 2).toInt()
            val yStart = (height * (1 - ANALYZE_RATIO) / 2).toInt()
            val xEnd   = width  - xStart
            val yEnd   = height - yStart

            var diffSum   = 0L
            var totalPx   = 0

            // Stride – chỉ lấy 1 pixel mỗi 4 pixel để nhanh hơn
            val stride = 4

            var y = yStart
            while (y < yEnd) {
                var x = xStart
                while (x < xEnd) {
                    val idx = y * width + x
                    if (idx < current.size && idx < prev.size) {
                        val diff = abs(
                            (current[idx].toInt() and 0xFF) -
                            (prev[idx].toInt()     and 0xFF)
                        )
                        if (diff > 20) diffSum++ // ngưỡng noise
                        totalPx++
                    }
                    x += stride
                }
                y += stride
            }

            return if (totalPx > 0) diffSum.toFloat() / totalPx else 0f
        }
    }

    // ── Đếm sự kiện chuyển động → kích hoạt wave ──────────────────────────

    private fun handleMotionEvent(now: Long) {
        // Trong cooldown → bỏ qua
        if (now - lastWaveMs < WAVE_COOLDOWN_MS) return

        if (motionCount == 0 || now - windowStartMs > WAVE_WINDOW_MS) {
            // Bắt đầu cửa sổ mới
            motionCount   = 1
            windowStartMs = now
        } else {
            motionCount++
        }

        Log.d(TAG, "Motion event #$motionCount (score triggered)")

        if (motionCount >= WAVE_COUNT) {
            Log.d(TAG, "✋ WAVE DETECTED via camera!")
            onWaveDetected()
            motionCount = 0
            lastWaveMs  = now
        }
    }
}
