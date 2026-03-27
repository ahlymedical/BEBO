package com.swiftshare

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.swiftshare.databinding.ItemDeviceBinding

class DeviceListAdapter(
    private val onDeviceClick: (WifiP2pDevice) -> Unit
) : ListAdapter<WifiP2pDevice, DeviceListAdapter.DeviceViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding, onDeviceClick)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DeviceViewHolder(
        private val binding: ItemDeviceBinding,
        private val onDeviceClick: (WifiP2pDevice) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: WifiP2pDevice) {
            binding.tvDeviceName.text = device.deviceName.takeIf { !it.isNullOrEmpty() } ?: "Unknown Device"

            val statusText = when (device.status) {
                WifiP2pDevice.AVAILABLE -> "Available"
                WifiP2pDevice.INVITED -> "Invited"
                WifiP2pDevice.CONNECTED -> "Connected"
                WifiP2pDevice.FAILED -> "Failed"
                WifiP2pDevice.UNAVAILABLE -> "Unavailable"
                else -> "Unknown"
            }
            binding.tvDeviceStatus.text = statusText

            binding.root.setOnClickListener {
                onDeviceClick(device)
            }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<WifiP2pDevice>() {
        override fun areItemsTheSame(oldItem: WifiP2pDevice, newItem: WifiP2pDevice): Boolean {
            return oldItem.deviceAddress == newItem.deviceAddress
        }

        @SuppressLint("DiffUtilEquals")
        override fun areContentsTheSame(oldItem: WifiP2pDevice, newItem: WifiP2pDevice): Boolean {
            return oldItem.deviceName == newItem.deviceName && oldItem.status == newItem.status
        }
    }
}
