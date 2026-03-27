package com.swiftshare

import android.net.wifi.p2p.WifiP2pDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TransferViewModel : ViewModel() {

    private val _discoveredDevices = MutableLiveData<List<WifiP2pDevice>>()
    val discoveredDevices: LiveData<List<WifiP2pDevice>> = _discoveredDevices

    private val _connectionStatus = MutableLiveData<ConnectionStatus>()
    val connectionStatus: LiveData<ConnectionStatus> = _connectionStatus

    private val _transferProgress = MutableLiveData<TransferProgress>()
    val transferProgress: LiveData<TransferProgress> = _transferProgress

    var connectedDeviceIp: String? = null
    var isGroupOwner: Boolean = false

    fun updateDevices(devices: List<WifiP2pDevice>) {
        _discoveredDevices.value = devices
    }

    fun updateConnectionStatus(status: ConnectionStatus) {
        _connectionStatus.value = status
    }

    fun updateTransferProgress(progress: TransferProgress) {
        _transferProgress.postValue(progress)
    }

    enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    data class TransferProgress(
        val percentage: Int,
        val speedMbPerSec: Double,
        val estimatedTimeRemainingSeconds: Long,
        val isTransferring: Boolean
    )
}
