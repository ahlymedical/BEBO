package com.swiftshare

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import android.text.format.Formatter
import com.swiftshare.databinding.FragmentTransferBinding
import java.util.Locale
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import java.io.File
import androidx.core.content.FileProvider

class TransferFragment : Fragment() {

    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TransferViewModel by viewModels()
    private lateinit var adapter: DeviceListAdapter

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: WifiDirectBroadcastReceiver

    private var httpServer: FileHttpServer? = null
    private var pendingShareType: ShareType = ShareType.WIFI_DIRECT

    enum class ShareType {
        WIFI_DIRECT, HTTP_LINK, QR_CODE, SHARE_SHEET, BLUETOOTH
    }
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FileTransferService.ACTION_TRANSFER_PROGRESS) {
                val percentage = intent.getIntExtra(FileTransferService.EXTRA_PROGRESS, 0)
                val speed = intent.getDoubleExtra(FileTransferService.EXTRA_SPEED, 0.0)
                val timeRemaining = intent.getLongExtra(FileTransferService.EXTRA_TIME_REMAINING, 0)
                val isTransferring = intent.getBooleanExtra(FileTransferService.EXTRA_IS_TRANSFERRING, false)

                viewModel.updateTransferProgress(
                    TransferViewModel.TransferProgress(percentage, speed, timeRemaining, isTransferring)
                )
            }
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            when (pendingShareType) {
                ShareType.WIFI_DIRECT -> sendFile(uri)
                ShareType.HTTP_LINK, ShareType.QR_CODE -> startHttpServer(uri, pendingShareType == ShareType.QR_CODE)
                ShareType.SHARE_SHEET -> shareViaAndroidSheet(uri)
                ShareType.BLUETOOTH -> shareViaBluetooth(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        wifiP2pManager = requireActivity().getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(requireContext(), requireActivity().mainLooper, null)

        setupRecyclerView()
        setupObservers()
        setupActionCards()
    }

    private fun setupActionCards() {
        // Toggle Switch setup
        binding.rgSendReceive.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == binding.rbSend.id) {
                binding.rbSend.setTextColor(requireContext().getColor(R.color.colorOnPrimary))
                binding.rbReceive.setTextColor(requireContext().getColor(R.color.colorPrimary))
                binding.gridShareSources.visibility = View.VISIBLE
            } else {
                binding.rbReceive.setTextColor(requireContext().getColor(R.color.colorOnPrimary))
                binding.rbSend.setTextColor(requireContext().getColor(R.color.colorPrimary))
                binding.gridShareSources.visibility = View.GONE
            }
        }

        // 2x2 Share Source Grid
        binding.cardSrcFiles.setOnClickListener { triggerFileSelection() }
        binding.cardSrcApps.setOnClickListener { showAppPicker() }
        binding.cardSrcDocs.setOnClickListener { triggerFileSelection("application/*") }
        binding.cardSrcAudio.setOnClickListener { triggerFileSelection("audio/*", "video/*") }

        // Share Methods (Chips)
        binding.chipGroupMethods.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            when (checkedIds.first()) {
                binding.chipWifi.id -> {
                    pendingShareType = ShareType.WIFI_DIRECT
                    binding.rvDevices.visibility = View.VISIBLE
                    binding.llQrContainer.visibility = View.GONE
                    discoverPeers()
                }
                binding.chipQrLink.id -> {
                    pendingShareType = ShareType.HTTP_LINK
                    binding.rvDevices.visibility = View.GONE
                    binding.llQrContainer.visibility = View.GONE
                }
                binding.chipQrScan.id -> {
                    pendingShareType = ShareType.QR_CODE
                    binding.rvDevices.visibility = View.GONE
                    binding.llQrContainer.visibility = View.GONE
                }
                binding.chipSheet.id -> {
                    pendingShareType = ShareType.SHARE_SHEET
                    binding.rvDevices.visibility = View.GONE
                    binding.llQrContainer.visibility = View.GONE
                }
                binding.chipBluetooth.id -> {
                    pendingShareType = ShareType.BLUETOOTH
                    binding.rvDevices.visibility = View.GONE
                    binding.llQrContainer.visibility = View.GONE
                }
                binding.chipNearby.id -> {
                    Toast.makeText(requireContext(), "Method coming soon", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareViaAndroidSheet(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = requireContext().contentResolver.getType(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share file via..."))
    }

    private fun shareViaBluetooth(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = requireContext().contentResolver.getType(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(shareIntent)
        } catch (e: Exception) {
            shareIntent.setPackage(null)
            startActivity(Intent.createChooser(shareIntent, "Share file via Bluetooth..."))
        }
    }

    private fun triggerFileSelection(vararg mimeTypes: String) {
        if (pendingShareType == ShareType.WIFI_DIRECT && viewModel.connectionStatus.value != TransferViewModel.ConnectionStatus.CONNECTED) {
            Toast.makeText(requireContext(), "Connect to a device first via WiFi", Toast.LENGTH_SHORT).show()
            return
        }

        val typeToLaunch = if (mimeTypes.isEmpty()) {
            "*/*"
        } else if (mimeTypes.size == 1) {
            mimeTypes[0]
        } else {
            // ActivityResultContracts.GetContent() only allows one string. For multiple, we'd need OpenDocument,
            // but for simplicity we'll just fall back to */* if multiple are requested, or pick the first.
            // A more robust approach uses OpenDocument contract, but this works for basic integration.
            "*/*"
        }
        filePickerLauncher.launch(typeToLaunch)
    }

    private fun showAppPicker() {
        if (pendingShareType == ShareType.WIFI_DIRECT && viewModel.connectionStatus.value != TransferViewModel.ConnectionStatus.CONNECTED) {
            Toast.makeText(requireContext(), "Connect to a device first via WiFi", Toast.LENGTH_SHORT).show()
            return
        }
        val pm = requireContext().packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } // Exclude system apps
            .sortedBy { pm.getApplicationLabel(it).toString() }

        if (apps.isEmpty()) {
            Toast.makeText(requireContext(), "No user apps found", Toast.LENGTH_SHORT).show()
            return
        }

        val appNames = apps.map { pm.getApplicationLabel(it).toString() }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.select_apps)
            .setItems(appNames) { _, which ->
                val selectedApp = apps[which]
                val apkFile = File(selectedApp.publicSourceDir)
                if (apkFile.exists()) {
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.provider",
                        apkFile
                    )
                    when (pendingShareType) {
                        ShareType.WIFI_DIRECT -> sendFile(uri)
                        ShareType.HTTP_LINK, ShareType.QR_CODE -> startHttpServer(uri, pendingShareType == ShareType.QR_CODE)
                        ShareType.SHARE_SHEET -> shareViaAndroidSheet(uri)
                        ShareType.BLUETOOTH -> shareViaBluetooth(uri)
                    }
                } else {
                    Toast.makeText(requireContext(), "APK not found", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupRecyclerView() {
        adapter = DeviceListAdapter { device ->
            connectToDevice(device)
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.discoveredDevices.observe(viewLifecycleOwner) { devices ->
            adapter.submitList(devices)
        }

        viewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
            binding.tvConnectionStatus.text = when(status) {
                TransferViewModel.ConnectionStatus.DISCONNECTED -> getString(R.string.status_disconnected)
                TransferViewModel.ConnectionStatus.CONNECTING -> getString(R.string.status_connecting)
                TransferViewModel.ConnectionStatus.CONNECTED -> getString(R.string.status_connected)
            }
        }

        viewModel.transferProgress.observe(viewLifecycleOwner) { progress ->
            if (progress.isTransferring) {
                binding.layoutTransferProgress.visibility = View.VISIBLE
                binding.progressBar.progress = progress.percentage

                val infoText = String.format(Locale.getDefault(), "Transferring: %d%% | %.2f MB/s | %d sec left",
                    progress.percentage, progress.speedMbPerSec, progress.estimatedTimeRemainingSeconds)
                binding.tvProgressInfo.text = infoText
            } else {
                binding.layoutTransferProgress.visibility = View.GONE
                if (progress.percentage == 100) {
                    Toast.makeText(requireContext(), "Transfer Complete", Toast.LENGTH_SHORT).show()
                } else if (progress.percentage == 0) {
                     Toast.makeText(requireContext(), "Transfer Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoverPeers() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(requireContext(), "Discovery Started", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reasonCode: Int) {
                Toast.makeText(requireContext(), "Discovery Failed: $reasonCode", Toast.LENGTH_SHORT).show()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        viewModel.updateConnectionStatus(TransferViewModel.ConnectionStatus.CONNECTING)

        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(requireContext(), "Connecting to ${device.deviceName}...", Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reason: Int) {
                Toast.makeText(requireContext(), "Connection failed: $reason", Toast.LENGTH_SHORT).show()
                viewModel.updateConnectionStatus(TransferViewModel.ConnectionStatus.DISCONNECTED)
            }
        })
    }

    private fun sendFile(uri: Uri) {
        val ip = viewModel.connectedDeviceIp
        if (ip == null) {
            Toast.makeText(requireContext(), "Device IP not found, wait a moment", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), FileTransferService::class.java).apply {
            action = FileTransferService.ACTION_SEND_FILE
            putExtra(FileTransferService.EXTRA_FILE_URI, uri.toString())
            putExtra(FileTransferService.EXTRA_HOST, ip)
            putExtra(FileTransferService.EXTRA_PORT, 8988)
        }
        requireContext().startService(intent)
    }

    private fun startReceiverService() {
        val intent = Intent(requireContext(), FileTransferService::class.java).apply {
            action = FileTransferService.ACTION_RECEIVE_FILE
            putExtra(FileTransferService.EXTRA_PORT, 8988)
        }
        requireContext().startService(intent)
    }

    private fun startHttpServer(fileUri: Uri, showQr: Boolean) {
        val port = 8080
        if (httpServer == null) {
            httpServer = FileHttpServer(port, requireContext().applicationContext, fileUri)
            try {
                httpServer?.start()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error starting HTTP server", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            httpServer?.setFileToServe(fileUri)
        }

        val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        val ipAddress = String.format(
            Locale.getDefault(),
            "%d.%d.%d.%d",
            ipInt and 0xFF,
            ipInt shr 8 and 0xFF,
            ipInt shr 16 and 0xFF,
            ipInt shr 24 and 0xFF
        )
        val downloadUrl = "http://$ipAddress:$port"

        binding.rvDevices.visibility = View.GONE
        binding.llQrContainer.visibility = View.VISIBLE
        binding.tvLinkUrl.text = downloadUrl

        if (showQr) {
            binding.ivQrCode.setImageBitmap(generateQrCode(downloadUrl))
            binding.ivQrCode.visibility = View.VISIBLE
        } else {
            binding.ivQrCode.visibility = View.GONE
        }
    }

    private fun generateQrCode(text: String): Bitmap? {
        try {
            val size = 512
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun onResume() {
        super.onResume()
        receiver = WifiDirectBroadcastReceiver(wifiP2pManager, channel,
            onPeersChanged = { peerListListener ->
                try {
                    @SuppressLint("MissingPermission")
                    val ignored = true
                } catch (e: Exception) {}
                wifiP2pManager.requestPeers(channel) { peers ->
                    viewModel.updateDevices(peers.deviceList.toList())
                }
            },
            onConnectionInfoAvailable = { connectionInfoListener ->
                wifiP2pManager.requestConnectionInfo(channel) { info ->
                    if (info.groupFormed) {
                        viewModel.updateConnectionStatus(TransferViewModel.ConnectionStatus.CONNECTED)
                        viewModel.isGroupOwner = info.isGroupOwner

                        if (info.isGroupOwner) {
                            // Start receiving as group owner
                            startReceiverService()
                        } else {
                            viewModel.connectedDeviceIp = info.groupOwnerAddress.hostAddress
                            Log.d("TransferFragment", "Group Owner IP: ${viewModel.connectedDeviceIp}")
                            // Start receiving as client too (P2P implies bidirectional capability)
                            startReceiverService()
                        }
                    }
                }
            },
            onStateChanged = { isEnabled ->
                if (!isEnabled) {
                    viewModel.updateDevices(emptyList())
                    viewModel.updateConnectionStatus(TransferViewModel.ConnectionStatus.DISCONNECTED)
                }
            }
        )
        requireActivity().registerReceiver(receiver, intentFilter)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireActivity().registerReceiver(
                progressReceiver,
                IntentFilter(FileTransferService.ACTION_TRANSFER_PROGRESS),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            requireActivity().registerReceiver(
                progressReceiver,
                IntentFilter(FileTransferService.ACTION_TRANSFER_PROGRESS)
            )
        }
    }

    override fun onPause() {
        super.onPause()
        requireActivity().unregisterReceiver(receiver)
        requireActivity().unregisterReceiver(progressReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        httpServer?.stop()
        _binding = null
    }
}
