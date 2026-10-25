package com.bleprovision.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bleprovision.app.R
import com.bleprovision.app.protocol.WifiApInfo

class WifiAdapter(private val onClick: (WifiApInfo) -> Unit) :
    RecyclerView.Adapter<WifiAdapter.ViewHolder>() {

    private val items = mutableListOf<WifiApInfo>()
    private var selectedSsid: String? = null

    fun submit(list: List<WifiApInfo>, selected: String?) {
        items.clear()
        items.addAll(list)
        selectedSsid = selected
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ssid: TextView = view.findViewById(R.id.wifiSsid)
        val signal: TextView = view.findViewById(R.id.wifiSignal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wifi, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ap = items[position]
        holder.ssid.text = ap.ssid
        holder.signal.text = buildString {
            if (ap.authRequired) append("🔒 ")
            append(
                when {
                    ap.rssi >= -55 -> "▂▄▆█"
                    ap.rssi >= -70 -> "▂▄▆"
                    ap.rssi >= -85 -> "▂▄"
                    else -> "▂"
                }
            )
        }
        holder.itemView.alpha = if (selectedSsid == null || selectedSsid == ap.ssid) 1f else 0.5f
        holder.itemView.setOnClickListener { onClick(ap) }
    }
}
