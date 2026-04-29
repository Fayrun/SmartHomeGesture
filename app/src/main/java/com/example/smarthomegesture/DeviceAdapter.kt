package com.example.smarthomegesture

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(
    private val devices: MutableList<SmartDevice>,
    private val listener: DeviceActionListener
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    interface DeviceActionListener {
        fun onTogglePower(device: SmartDevice, isOn: Boolean)
        fun onVolumeChanged(device: SmartDevice, volume: Int)
        fun onDeviceClick(device: SmartDevice)
        fun onConnectClick(device: SmartDevice)
    }

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card:          CardView  = view.findViewById(R.id.cardDevice)
        val iconDevice:    ImageView = view.findViewById(R.id.imgDeviceIcon)
        val tvName:        TextView  = view.findViewById(R.id.tvDeviceName)
        val tvStatus:      TextView  = view.findViewById(R.id.tvDeviceStatus)
        val tvConnection:  TextView  = view.findViewById(R.id.tvConnectionType)
        val tvConnectStatus: TextView = view.findViewById(R.id.tvConnectStatus)
        val switchPower:   Switch    = view.findViewById(R.id.switchPower)
        val seekVolume:    SeekBar   = view.findViewById(R.id.seekVolume)
        val btnConnect:    Button    = view.findViewById(R.id.btnConnect)
        val layoutVolume:  View      = view.findViewById(R.id.layoutVolume)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        val ctx = holder.itemView.context

        // Tên và trạng thái bật/tắt
        holder.tvName.text = device.name
        holder.tvStatus.text = if (device.isOn) "Đang bật" else "Đang tắt"
        holder.tvConnection.text = device.connectionType.name

        // Trạng thái kết nối
        updateConnectionStatus(holder, device)

        // Icon theo loại thiết bị
        holder.iconDevice.setImageResource(
            when (device.type) {
                DeviceType.LIGHT   -> R.drawable.ic_light
                DeviceType.FAN     -> R.drawable.ic_fan
                DeviceType.TV      -> R.drawable.ic_tv
                DeviceType.SPEAKER -> R.drawable.ic_speaker
            }
        )

        // Màu card theo trạng thái bật/tắt
        holder.card.setCardBackgroundColor(
            ctx.getColor(if (device.isOn) R.color.card_on else R.color.card_off)
        )

        // Switch bật/tắt
        holder.switchPower.setOnCheckedChangeListener(null)
        holder.switchPower.isChecked = device.isOn
        holder.switchPower.setOnCheckedChangeListener { _, isChecked ->
            device.isOn = isChecked
            holder.tvStatus.text = if (isChecked) "Đang bật" else "Đang tắt"
            holder.card.setCardBackgroundColor(
                ctx.getColor(if (isChecked) R.color.card_on else R.color.card_off)
            )
            listener.onTogglePower(device, isChecked)
        }

        // Volume seekbar (chỉ TV và Speaker)
        holder.layoutVolume.visibility =
            if (device.type == DeviceType.TV || device.type == DeviceType.SPEAKER)
                View.VISIBLE else View.GONE
        holder.seekVolume.progress = device.volume
        holder.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) { device.volume = p; listener.onVolumeChanged(device, p) }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // Nút kết nối – toggle giả lập
        holder.btnConnect.setOnClickListener {
            if (device.isConnected) {
                // Ngắt kết nối giả lập
                device.isConnected = false
                updateConnectionStatus(holder, device)
            } else {
                // Gọi listener (MainActivity xử lý kết nối thật hoặc giả lập)
                listener.onConnectClick(device)
            }
        }

        holder.card.setOnClickListener { listener.onDeviceClick(device) }
    }

    private fun updateConnectionStatus(holder: DeviceViewHolder, device: SmartDevice) {
        val ctx = holder.itemView.context
        if (device.isConnected) {
            holder.tvConnectStatus.text = "● Đã kết nối"
            holder.tvConnectStatus.setTextColor(ctx.getColor(R.color.accent_green))
            holder.btnConnect.text = "Ngắt"
            holder.btnConnect.backgroundTintList =
                ctx.getColorStateList(R.color.accent_red)
        } else {
            holder.tvConnectStatus.text = "● Chưa kết nối"
            holder.tvConnectStatus.setTextColor(ctx.getColor(R.color.accent_red))
            holder.btnConnect.text = "Kết nối"
            holder.btnConnect.backgroundTintList =
                ctx.getColorStateList(R.color.accent_blue)
        }
    }

    override fun getItemCount(): Int = devices.size

    fun updateDevice(updated: SmartDevice) {
        val idx = devices.indexOfFirst { it.id == updated.id }
        if (idx >= 0) { devices[idx] = updated; notifyItemChanged(idx) }
    }

    fun addDevice(device: SmartDevice) {
        devices.add(device)
        notifyItemInserted(devices.size - 1)
    }
}
