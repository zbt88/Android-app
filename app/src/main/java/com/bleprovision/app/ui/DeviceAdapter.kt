package com.bleprovision.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bleprovision.app.R
import com.bleprovision.app.ble.BleDevice

class DeviceAdapter(private val onClick: (BleDevice) -> Unit) :
    RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    private val items = mutableListOf<BleDevice>()

    fun submit(list: List<BleDevice>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.deviceName)
        val rssi: TextView = view.findViewById(R.id.deviceRssi)
        val signal: TextView = view.findViewById(R.id.deviceSignal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dev = items[position]
        holder.name.text = dev.name
        holder.rssi.text = "${dev.rssi} dBm"
        holder.signal.text = signalText(dev.rssi)
        holder.itemView.setOnClickListener { onClick(dev) }
    }

    companion object {
        fun signalText(rssi: Int): String = when {
            rssi >= -55 -> "信号很好 ▂▄▆█"
            rssi >= -70 -> "信号良好 ▂▄▆"
            rssi >= -85 -> "信号一般 ▂▄"
            else -> "信号弱 ▂"
        }
    }
}
