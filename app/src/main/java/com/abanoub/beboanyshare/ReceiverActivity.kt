package com.abanoub.beboanyshare

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.abanoub.beboanyshare.databinding.ActivityReceiverBinding
import java.util.Locale
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.abanoub.beboanyshare.databinding.ItemIncomingTransferBinding

class ReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiverBinding
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: WifiDirectBroadcastReceiver

    private val incomingTransfers = mutableListOf<IncomingTransfer>()
    private lateinit var transferAdapter: IncomingTransferAdapter

    data class IncomingTransfer(
        val id: String,
        val fileName: String,
        var isAccepted: Boolean = false,
        var isRejected: Boolean = false
    )

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FileTransferService.ACTION_TRANSFER_PROGRESS) {
                val percentage = intent.getIntExtra(FileTransferService.EXTRA_PROGRESS, 0)
                val speed = intent.getDoubleExtra(FileTransferService.EXTRA_SPEED, 0.0)
                val isTransferring = intent.getBooleanExtra(FileTransferService.EXTRA_IS_TRANSFERRING, false)

                if (isTransferring) {
                    binding.layoutTransferProgress.visibility = View.VISIBLE
                    binding.tvWaitingStatus.text = "Receiving file..."
                    binding.progressBar.progress = percentage
                    binding.tvSpeed.text = String.format(Locale.getDefault(), "%.2f MB/s", speed)
                    binding.tvProgressInfo.text = "$percentage%"
                } else {
                    binding.layoutTransferProgress.visibility = View.GONE
                    binding.tvWaitingStatus.text = if (percentage == 100) "Transfer Complete!" else "Waiting for connection..."
                }
            }
        }
    }

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val url = result.contents
            Toast.makeText(this, "Scanned: $url", Toast.LENGTH_LONG).show()
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to open link", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnScanQr.setOnClickListener {
            barcodeLauncher.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("Scan BEBO Any Share QR Code")
                setCameraId(0)
                setBeepEnabled(true)
                setBarcodeImageEnabled(false)
            })
        }

        setupRecyclerView()
        discoverPeers()
    }

    private fun setupRecyclerView() {
        transferAdapter = IncomingTransferAdapter(
            onAccept = { transfer ->
                transfer.isAccepted = true
                transferAdapter.notifyDataSetChanged()
                startReceiverService()
            },
            onReject = { transfer ->
                incomingTransfers.remove(transfer)
                transferAdapter.notifyDataSetChanged()
            }
        )
        binding.rvIncomingTransfers.layoutManager = LinearLayoutManager(this)
        binding.rvIncomingTransfers.adapter = transferAdapter
        transferAdapter.submitList(incomingTransfers)
    }

    class IncomingTransferAdapter(
        private val onAccept: (IncomingTransfer) -> Unit,
        private val onReject: (IncomingTransfer) -> Unit
    ) : RecyclerView.Adapter<IncomingTransferAdapter.ViewHolder>() {

        private var items = listOf<IncomingTransfer>()

        fun submitList(list: List<IncomingTransfer>) {
            items = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemIncomingTransferBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(val binding: ItemIncomingTransferBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(transfer: IncomingTransfer) {
                binding.tvFileName.text = transfer.fileName
                binding.btnAccept.isEnabled = !transfer.isAccepted
                binding.btnReject.isEnabled = !transfer.isAccepted

                binding.btnAccept.setOnClickListener { onAccept(transfer) }
                binding.btnReject.setOnClickListener { onReject(transfer) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoverPeers() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reasonCode: Int) {}
        })
    }

    private fun startReceiverService() {
        val intent = Intent(this, FileTransferService::class.java).apply {
            action = FileTransferService.ACTION_RECEIVE_FILE
            putExtra(FileTransferService.EXTRA_PORT, 8988)
        }
        startService(intent)
    }

    override fun onResume() {
        super.onResume()
        receiver = WifiDirectBroadcastReceiver(wifiP2pManager, channel,
            onPeersChanged = {},
            onConnectionInfoAvailable = {
                wifiP2pManager.requestConnectionInfo(channel) { info ->
                    if (info.groupFormed) {
                        // Mock incoming transfer request
                        if (incomingTransfers.isEmpty()) {
                            incomingTransfers.add(IncomingTransfer("1", "incoming_file_${System.currentTimeMillis()}"))
                            transferAdapter.submitList(incomingTransfers)
                        }
                    }
                }
            },
            onStateChanged = { }
        )
        registerReceiver(receiver, IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(progressReceiver, IntentFilter(FileTransferService.ACTION_TRANSFER_PROGRESS), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(progressReceiver, IntentFilter(FileTransferService.ACTION_TRANSFER_PROGRESS))
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
        unregisterReceiver(progressReceiver)
    }
}
