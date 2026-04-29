package com.example.smarthomegesture

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Màn hình điều khiển chi tiết từng thiết bị IoT.
 * Cho phép: bật/tắt, điều chỉnh âm lượng, chuyển bài,
 * và cấu hình địa chỉ kết nối (MAC / IP).
 */
class DeviceControlActivity : AppCompatActivity() {

    private lateinit var tvDeviceName:   TextView
    private lateinit var tvDeviceType:   TextView
    private lateinit var tvStatus:       TextView
    private lateinit var switchPower:    Switch
    private lateinit var seekVolume:     SeekBar
    private lateinit var tvVolume:       TextView
    private lateinit var btnPrev:        Button
    private lateinit var btnNext:        Button
    private lateinit var btnConnect:     Button
    private lateinit var etAddress:      EditText
    private lateinit var rgConnection:   RadioGroup
    private lateinit var layoutVolume:   View
    private lateinit var layoutTrack:    View

    private lateinit var bluetoothController: BluetoothController
    private lateinit var wifiController:      WiFiController
    private lateinit var repository:          DeviceRepository
    private var device: SmartDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_control)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        bluetoothController = BluetoothController(this)
        wifiController      = WiFiController()
        repository          = DeviceRepository(this)

        initViews()

        // Tải thiết bị từ repository theo id được truyền vào
        val deviceId = intent.getStringExtra("device_id")
        if (deviceId != null) {
            device = repository.getDevices().firstOrNull { it.id == deviceId }
        }
        device?.let { bindDevice(it) }
    }

    private fun initViews() {
        tvDeviceName  = findViewById(R.id.tvDetailDeviceName)
        tvDeviceType  = findViewById(R.id.tvDetailDeviceType)
        tvStatus      = findViewById(R.id.tvDetailStatus)
        switchPower   = findViewById(R.id.switchDetailPower)
        seekVolume    = findViewById(R.id.seekDetailVolume)
        tvVolume      = findViewById(R.id.tvDetailVolume)
        btnPrev       = findViewById(R.id.btnPrevTrack)
        btnNext       = findViewById(R.id.btnNextTrack)
        btnConnect    = findViewById(R.id.btnDetailConnect)
        etAddress     = findViewById(R.id.etDeviceAddress)
        rgConnection  = findViewById(R.id.rgConnectionType)
        layoutVolume  = findViewById(R.id.layoutDetailVolume)
        layoutTrack   = findViewById(R.id.layoutTrackControl)
    }

    private fun bindDevice(d: SmartDevice) {
        title = d.name
        tvDeviceName.text = d.name
        tvDeviceType.text = d.type.name
        tvStatus.text = if (d.isOn) "Đang bật" else "Đang tắt"
        switchPower.isChecked = d.isOn
        seekVolume.progress = d.volume
        tvVolume.text = "${d.volume}%"
        etAddress.setText(d.address)

        // Hiển thị controls phù hợp theo loại thiết bị
        layoutVolume.visibility =
            if (d.type == DeviceType.SPEAKER || d.type == DeviceType.TV) View.VISIBLE else View.GONE
        layoutTrack.visibility =
            if (d.type == DeviceType.SPEAKER || d.type == DeviceType.TV) View.VISIBLE else View.GONE

        // Radio group connection type
        rgConnection.check(
            if (d.connectionType == ConnectionType.BLUETOOTH) R.id.rbBluetooth else R.id.rbWifi
        )

        // Power switch
        switchPower.setOnCheckedChangeListener { _, isOn ->
            d.isOn = isOn
            tvStatus.text = if (isOn) "Đang bật" else "Đang tắt"
            repository.updateDevice(d)
            sendCommand(DeviceCommand(d.id, CommandAction.TOGGLE_POWER, if (isOn) 1 else 0))
        }

        // Volume seekbar
        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    d.volume = progress
                    tvVolume.text = "$progress%"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                repository.updateDevice(d)
                sendCommand(DeviceCommand(d.id, CommandAction.SET_VOLUME, d.volume))
            }
        })

        // Track buttons
        btnPrev.setOnClickListener {
            sendCommand(DeviceCommand(d.id, CommandAction.PREV_TRACK))
            Toast.makeText(this, "⏮ Bài trước", Toast.LENGTH_SHORT).show()
        }
        btnNext.setOnClickListener {
            sendCommand(DeviceCommand(d.id, CommandAction.NEXT_TRACK))
            Toast.makeText(this, "⏭ Bài tiếp theo", Toast.LENGTH_SHORT).show()
        }

        // Kết nối
        btnConnect.setOnClickListener {
            val addr = etAddress.text.toString().trim()
            d.address = addr
            d.connectionType = if (rgConnection.checkedRadioButtonId == R.id.rbBluetooth)
                ConnectionType.BLUETOOTH else ConnectionType.WIFI
            repository.updateDevice(d)
            connectDevice(d)
        }
    }

    private fun sendCommand(command: DeviceCommand) {
        val d = device ?: return
        lifecycleScope.launch {
            when (d.connectionType) {
                ConnectionType.BLUETOOTH -> bluetoothController.sendCommand(command)
                ConnectionType.WIFI      -> wifiController.sendCommand(d.address, command)
            }
        }
    }

    private fun connectDevice(d: SmartDevice) {
        lifecycleScope.launch {
            val ok = when (d.connectionType) {
                ConnectionType.BLUETOOTH -> bluetoothController.connectToDevice(d.address)
                ConnectionType.WIFI      -> wifiController.ping(d.address)
            }
            d.isConnected = ok
            repository.updateDevice(d)
            runOnUiThread {
                Toast.makeText(
                    this@DeviceControlActivity,
                    if (ok) "✅ Kết nối thành công" else "❌ Kết nối thất bại",
                    Toast.LENGTH_SHORT
                ).show()
                btnConnect.text = if (ok) "Ngắt kết nối" else "Kết nối"
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
