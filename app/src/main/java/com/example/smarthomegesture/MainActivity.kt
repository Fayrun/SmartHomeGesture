package com.example.smarthomegesture

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity – Màn hình chính của ứng dụng.
 *
 * Trách nhiệm:
 *  1. Khởi tạo và quản lý GestureDetectorHelper
 *  2. Nhận cử chỉ → gửi lệnh đến thiết bị được chọn
 *  3. Hiển thị danh sách thiết bị IoT
 *  4. Hiển thị dữ liệu sensor real-time
 */
class MainActivity : AppCompatActivity(), GestureListener, DeviceAdapter.DeviceActionListener {

    // ── View References ────────────────────────────────────────────────────
    private lateinit var tvGestureStatus:   TextView
    private lateinit var tvAccelData:       TextView
    private lateinit var tvGyroData:        TextView
    private lateinit var tvTargetDevice:    TextView
    private lateinit var progressVolume:    ProgressBar
    private lateinit var tvVolumeValue:     TextView
    private lateinit var rvDevices:         RecyclerView
    private lateinit var layoutGestureFeedback: View
    private lateinit var spinnerDevice:     Spinner
    private lateinit var tvProxStatus:      TextView
    private lateinit var cardCameraWave:    View
    private lateinit var tvCameraStatus:    TextView
    private lateinit var cameraPreviewView: androidx.camera.view.PreviewView

    // ── Logic Components ───────────────────────────────────────────────────
    private lateinit var sensorManager:      SensorManager
    private lateinit var gestureDetector:    GestureDetectorHelper
    private lateinit var bluetoothController: BluetoothController
    private lateinit var wifiController:     WiFiController
    private lateinit var deviceRepository:   DeviceRepository
    private lateinit var deviceAdapter:      DeviceAdapter
    private lateinit var audioManager:       AudioManager
    private var cameraWaveDetector:          CameraWaveDetector? = null
    private var isCameraWaveOn = false

    private var devices = mutableListOf<SmartDevice>()
    private var selectedDeviceIndex = 0
    private var currentVolume = 50

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
        private const val REQUEST_LOCATION_PERMISSION   = 1002
        private const val REQUEST_ENABLE_BT             = 1003
        private const val REQUEST_CAMERA_PERMISSION     = 1004
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initComponents()
        checkPermissions()
        setupDeviceList()
        setupDeviceSpinner()
    }

    override fun onResume() {
        super.onResume()
        gestureDetector.start()
    }

    override fun onPause() {
        super.onPause()
        gestureDetector.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothController.disconnect()
        cameraWaveDetector?.stop()
    }

    // ── Khởi tạo ──────────────────────────────────────────────────────────

    private fun initViews() {
        tvGestureStatus     = findViewById(R.id.tvGestureStatus)
        tvAccelData         = findViewById(R.id.tvAccelData)
        tvGyroData          = findViewById(R.id.tvGyroData)
        tvTargetDevice      = findViewById(R.id.tvTargetDevice)
        progressVolume      = findViewById(R.id.progressVolume)
        tvVolumeValue       = findViewById(R.id.tvVolumeValue)
        rvDevices           = findViewById(R.id.rvDevices)
        layoutGestureFeedback = findViewById(R.id.layoutGestureFeedback)
        spinnerDevice       = findViewById(R.id.spinnerTargetDevice)
        tvProxStatus        = findViewById(R.id.tvProxStatus)
        cardCameraWave      = findViewById(R.id.cardCameraWave)
        tvCameraStatus      = findViewById(R.id.tvCameraStatus)
        cameraPreviewView   = findViewById(R.id.cameraPreviewView)

        // Nút điều hướng
        findViewById<View>(R.id.btnScanBluetooth).setOnClickListener {
            startActivity(Intent(this, BluetoothScanActivity::class.java))
        }
        findViewById<View>(R.id.btnManageDevices).setOnClickListener {
            startActivity(Intent(this, DeviceControlActivity::class.java))
        }
        // Nút bật/tắt camera wave
        findViewById<View>(R.id.btnToggleCamera).setOnClickListener {
            toggleCameraWave()
        }
    }

    private fun initComponents() {
        sensorManager       = getSystemService(SENSOR_SERVICE) as SensorManager
        audioManager        = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        gestureDetector     = GestureDetectorHelper(sensorManager, this)
        bluetoothController = BluetoothController(this)
        wifiController      = WiFiController()
        deviceRepository    = DeviceRepository(this)

        // Đồng bộ currentVolume với âm lượng Media thực của máy
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        currentVolume = (curVol.toFloat() / maxVol * 100).toInt()

        // Hiển thị trạng thái Proximity
        if (gestureDetector.hasProximity()) {
            tvProxStatus.text = "👁 Proximity: ✅ Sẵn sàng"
            tvProxStatus.setTextColor(getColor(R.color.accent_green))
        } else {
            tvProxStatus.text = "👁 Proximity: ❌ Không có"
            tvProxStatus.setTextColor(getColor(R.color.accent_red))
        }

        // Hiển thị thông báo nếu không có gyroscope
        if (!gestureDetector.hasGyroscope()) {
            tvGyroData.text = "Thiết bị không có Gyroscope\n(Tính năng xoay không khả dụng)"
        }
    }

    private fun setupDeviceList() {
        devices = deviceRepository.getDevices()
        deviceAdapter = DeviceAdapter(devices, this)
        rvDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }
    }

    private fun setupDeviceSpinner() {
        val names = devices.map { it.name }.toMutableList()
        names.add(0, "Tất cả thiết bị")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDevice.adapter = adapter
        spinnerDevice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedDeviceIndex = pos - 1   // -1 = tất cả
                tvTargetDevice.text = if (pos == 0) "Tất cả thiết bị" else devices[pos - 1].name
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ── GestureListener callbacks ──────────────────────────────────────────

    /** Cử chỉ vẫy tay (từ Accelerometer/Proximity) → bật/tắt đèn */
    override fun onWaveDetected() {
        triggerWaveAction()
    }

    /** Cử chỉ nghiêng → điều chỉnh âm lượng THẬT của điện thoại */
    override fun onTiltDetected(direction: TiltDirection) {
        // Tăng/giảm âm lượng Media thực của hệ thống
        val flag = AudioManager.FLAG_SHOW_UI  // hiện thanh âm lượng hệ thống
        if (direction == TiltDirection.UP) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE, flag)
        } else {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER, flag)
        }

        // Đọc lại âm lượng thực để cập nhật UI
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        currentVolume = (curVol.toFloat() / maxVol * 100).toInt()

        runOnUiThread {
            val arrow = if (direction == TiltDirection.UP) "🔊 Tăng âm lượng" else "🔉 Giảm âm lượng"
            showGestureFeedback("📱 Nghiêng – $arrow: $currentVolume%")
            progressVolume.progress = currentVolume
            tvVolumeValue.text = "$currentVolume%"
        }

        // Vẫn gửi lệnh đến IoT device nếu đang kết nối
        val targets = getTargetDevices(DeviceType.SPEAKER) +
                      getTargetDevices(DeviceType.TV)
        targets.forEach { device ->
            device.volume = currentVolume
            sendCommandToDevice(device, DeviceCommand(device.id, CommandAction.SET_VOLUME, currentVolume))
        }
    }

    /** Cử chỉ xoay → chuyển bài nhạc THẬT (gửi media key đến app đang phát) */
    override fun onRotateDetected(direction: RotateDirection) {
        val keyCode = if (direction == RotateDirection.RIGHT)
            KeyEvent.KEYCODE_MEDIA_NEXT else KeyEvent.KEYCODE_MEDIA_PREVIOUS

        // Gửi media key event – hoạt động với Spotify, YouTube Music, nhạc hệ thống...
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent   = KeyEvent(KeyEvent.ACTION_UP,   keyCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)

        runOnUiThread {
            val label = if (direction == RotateDirection.RIGHT) "⏭ Bài tiếp theo" else "⏮ Bài trước"
            showGestureFeedback("🔄 Xoay – $label")
        }

        // Vẫn gửi lệnh đến IoT device nếu đang kết nối
        val action = if (direction == RotateDirection.RIGHT)
            CommandAction.NEXT_TRACK else CommandAction.PREV_TRACK
        val targets = getTargetDevices(DeviceType.SPEAKER) +
                      getTargetDevices(DeviceType.TV)
        targets.forEach { device ->
            sendCommandToDevice(device, DeviceCommand(device.id, action))
        }
    }

    /** Cập nhật hiển thị dữ liệu Accelerometer */
    override fun onSensorDataUpdated(x: Float, y: Float, z: Float, magnitude: Float) {
        runOnUiThread {
            tvAccelData.text = "Accelerometer\n" +
                    "X: ${f(x)} m/s²\n" +
                    "Y: ${f(y)} m/s²\n" +
                    "Z: ${f(z)} m/s²\n" +
                    "|a|: ${f(magnitude)} m/s²"
        }
    }

    /** Cập nhật hiển thị dữ liệu Gyroscope */
    override fun onGyroDataUpdated(x: Float, y: Float, z: Float) {
        runOnUiThread {
            tvGyroData.text = "Gyroscope\n" +
                    "X: ${f(x)} rad/s\n" +
                    "Y: ${f(y)} rad/s\n" +
                    "Z: ${f(z)} rad/s"
        }
    }

    // ── DeviceAdapter callbacks ────────────────────────────────────────────

    override fun onTogglePower(device: SmartDevice, isOn: Boolean) {
        deviceRepository.updateDevice(device)
        sendCommandToDevice(device, DeviceCommand(device.id, CommandAction.TOGGLE_POWER, if (isOn) 1 else 0))
        Snackbar.make(rvDevices,
            "${device.name} ${if (isOn) "đã bật" else "đã tắt"}",
            Snackbar.LENGTH_SHORT).show()
    }

    override fun onVolumeChanged(device: SmartDevice, volume: Int) {
        deviceRepository.updateDevice(device)
        sendCommandToDevice(device, DeviceCommand(device.id, CommandAction.SET_VOLUME, volume))
    }

    override fun onDeviceClick(device: SmartDevice) {
        val intent = Intent(this, DeviceControlActivity::class.java)
        intent.putExtra("device_id", device.id)
        startActivity(intent)
    }

    override fun onConnectClick(device: SmartDevice) {
        // Hiện dialog chọn: kết nối thật hay giả lập (để demo)
        val options = arrayOf("🔌 Giả lập kết nối (Demo)", "📡 Kết nối thiết bị thật")
        android.app.AlertDialog.Builder(this)
            .setTitle("Kết nối – ${device.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> simulateConnection(device)   // Demo mode
                    1 -> when (device.connectionType) {
                        ConnectionType.BLUETOOTH -> connectBluetooth(device)
                        ConnectionType.WIFI      -> connectWifi(device)
                    }
                }
            }
            .show()
    }

    /** Giả lập kết nối thành công – dùng để demo khi không có thiết bị thật */
    private fun simulateConnection(device: SmartDevice) {
        device.isConnected = true
        deviceRepository.updateDevice(device)
        deviceAdapter.updateDevice(device)
        Toast.makeText(
            this,
            "✅ [Demo] ${device.name} đã kết nối ${device.connectionType.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ── Kết nối thiết bị ──────────────────────────────────────────────────

    private fun connectBluetooth(device: SmartDevice) {
        if (device.address.isBlank()) {
            startActivity(Intent(this, BluetoothScanActivity::class.java))
            return
        }
        lifecycleScope.launch {
            val ok = bluetoothController.connectToDevice(device.address)
            device.isConnected = ok
            deviceRepository.updateDevice(device)
            runOnUiThread {
                deviceAdapter.updateDevice(device)
                Toast.makeText(
                    this@MainActivity,
                    if (ok) "✅ Đã kết nối Bluetooth: ${device.name}"
                    else    "❌ Kết nối thất bại",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun connectWifi(device: SmartDevice) {
        if (device.address.isBlank()) {
            Toast.makeText(this, "Chưa cấu hình địa chỉ IP", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val ok = wifiController.ping(device.address)
            device.isConnected = ok
            deviceRepository.updateDevice(device)
            runOnUiThread {
                deviceAdapter.updateDevice(device)
                Toast.makeText(
                    this@MainActivity,
                    if (ok) "✅ WiFi kết nối: ${device.address}"
                    else    "❌ Không tìm thấy thiết bị",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── Gửi lệnh đến thiết bị ─────────────────────────────────────────────

    private fun sendCommandToDevice(device: SmartDevice, command: DeviceCommand) {
        lifecycleScope.launch {
            when (device.connectionType) {
                ConnectionType.BLUETOOTH -> {
                    if (bluetoothController.isConnected()) {
                        bluetoothController.sendCommand(command)
                    }
                }
                ConnectionType.WIFI -> {
                    if (device.address.isNotBlank()) {
                        wifiController.sendCommand(device.address, command)
                    }
                }
            }
        }
    }

    /** Lấy danh sách thiết bị mục tiêu (theo loại hoặc tất cả) */
    private fun getTargetDevices(type: DeviceType): List<SmartDevice> {
        return if (selectedDeviceIndex < 0) {
            devices.filter { it.type == type }
        } else {
            val d = devices.getOrNull(selectedDeviceIndex)
            if (d?.type == type) listOf(d) else emptyList()
        }
    }

    // ── Hiển thị feedback cử chỉ ──────────────────────────────────────────

    private fun showGestureFeedback(text: String) {
        tvGestureStatus.text = text
        layoutGestureFeedback.apply {
            alpha = 1f
            animate().alpha(0f).setDuration(2000).start()
        }
    }

    // ── Camera Wave Detector ───────────────────────────────────────────────

    private fun toggleCameraWave() {
        if (!isCameraWaveOn) {
            // Kiểm tra quyền camera trước
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION
                )
                return
            }
            startCameraWave()
        } else {
            stopCameraWave()
        }
    }

    private fun startCameraWave() {
        cameraWaveDetector = CameraWaveDetector(
            context         = this,
            lifecycleOwner  = this,
            onWaveDetected  = { triggerWaveAction() }
        ).also {
            it.previewView = cameraPreviewView
            it.start()
        }
        isCameraWaveOn = true
        cardCameraWave.visibility = View.VISIBLE
        tvCameraStatus.text = "● Đang chạy"
        tvCameraStatus.setTextColor(getColor(R.color.accent_green))
        Toast.makeText(this, "📷 Camera wave BẬT – Vẫy tay 2 lần để bật/tắt đèn", Toast.LENGTH_SHORT).show()
    }

    private fun stopCameraWave() {
        cameraWaveDetector?.stop()
        cameraWaveDetector = null
        isCameraWaveOn = false
        cardCameraWave.visibility = View.GONE
        Toast.makeText(this, "📷 Camera wave TẮT", Toast.LENGTH_SHORT).show()
    }

    /** Callback chung cho wave (từ camera hoặc sensor) */
    private fun triggerWaveAction() {
        runOnUiThread { showGestureFeedback("✋ Vẫy tay – Bật/Tắt đèn") }
        val targets = getTargetDevices(DeviceType.LIGHT)
        targets.forEach { device ->
            device.isOn = !device.isOn
            deviceRepository.updateDevice(device)
            sendCommandToDevice(device, DeviceCommand(device.id, CommandAction.TOGGLE_POWER,
                if (device.isOn) 1 else 0))
            runOnUiThread { deviceAdapter.updateDevice(device) }
        }
    }

    // ── Quyền truy cập ────────────────────────────────────────────────────

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS)
        } else {
            checkBluetoothEnabled()
        }
    }

    private fun checkBluetoothEnabled() {
        if (!bluetoothController.isBluetoothEnabled()) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(intent, REQUEST_ENABLE_BT)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> checkBluetoothEnabled()
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startCameraWave()
                } else {
                    Toast.makeText(this, "❌ Cần quyền Camera để dùng tính năng này",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Format float 2 chữ số thập phân */
    private fun f(v: Float): String = "%.2f".format(v)
}
