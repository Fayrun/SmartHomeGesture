package com.example.smarthomegesture

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Màn hình quét và chọn thiết bị Bluetooth để ghép đôi.
 */
class BluetoothScanActivity : AppCompatActivity() {

    private lateinit var rvPaired:    RecyclerView
    private lateinit var btnScan:     Button
    private lateinit var progressScan: ProgressBar
    private lateinit var tvNoDevices: TextView

    private lateinit var btController: BluetoothController
    private lateinit var repository:   DeviceRepository
    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private lateinit var btAdapter: BtDeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_scan)
        supportActionBar?.title = "Quét Bluetooth"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        btController = BluetoothController(this)
        repository   = DeviceRepository(this)

        rvPaired     = findViewById(R.id.rvPairedDevices)
        btnScan      = findViewById(R.id.btnStartScan)
        progressScan = findViewById(R.id.progressScan)
        tvNoDevices  = findViewById(R.id.tvNoPairedDevices)

        btAdapter = BtDeviceAdapter(pairedDevices) { device ->
            onDeviceSelected(device)
        }
        rvPaired.layoutManager = LinearLayoutManager(this)
        rvPaired.adapter = btAdapter

        btnScan.setOnClickListener { loadPairedDevices() }
        loadPairedDevices()
    }

    private fun loadPairedDevices() {
        progressScan.visibility = View.VISIBLE
        pairedDevices.clear()
        pairedDevices.addAll(btController.getPairedDevices())
        btAdapter.notifyDataSetChanged()
        progressScan.visibility = View.GONE

        tvNoDevices.visibility = if (pairedDevices.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onDeviceSelected(btDevice: BluetoothDevice) {
        val name = if (btController.hasBluetoothPermissions()) btDevice.name ?: "Unknown"
                   else "Unknown"
        Toast.makeText(this, "Đã chọn: $name", Toast.LENGTH_SHORT).show()
        // Có thể mở dialog để gán thiết bị này cho SmartDevice nào
        setResult(RESULT_OK, Intent().apply {
            putExtra("bt_address", btDevice.address)
            putExtra("bt_name", name)
        })
        finish()
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressed(); return true }

    // ── Inner adapter ──────────────────────────────────────────────────────
    inner class BtDeviceAdapter(
        private val list: List<BluetoothDevice>,
        private val onClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.Adapter<BtDeviceAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName:    TextView = v.findViewById(R.id.tvBtDeviceName)
            val tvAddress: TextView = v.findViewById(R.id.tvBtAddress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bluetooth_device, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val dev = list[position]
            holder.tvName.text    = if (btController.hasBluetoothPermissions()) dev.name ?: "Unknown" else "Unknown"
            holder.tvAddress.text = dev.address
            holder.itemView.setOnClickListener { onClick(dev) }
        }

        override fun getItemCount() = list.size
    }
}
