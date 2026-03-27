package com.abanoub.beboanyshare

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.abanoub.beboanyshare.databinding.ActivitySenderBinding
import java.io.File
import java.util.Locale

class SenderActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySenderBinding
    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: WifiDirectBroadcastReceiver

    private lateinit var deviceAdapter: DeviceListAdapter
    private var selectedFileUri: Uri? = null
    private var httpServer: FileHttpServer? = null
    private var connectedIp: String? = null

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            binding.tvSelectedFiles.text = "File selected: ${uri.lastPathSegment}"
        }
    }

    private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FileTransferService.ACTION_TRANSFER_PROGRESS) {
                val percentage = intent.getIntExtra(FileTransferService.EXTRA_PROGRESS, 0)
                val speed = intent.getDoubleExtra(FileTransferService.EXTRA_SPEED, 0.0)
                val timeRemaining = intent.getLongExtra(FileTransferService.EXTRA_TIME_REMAINING, 0)
                val isTransferring = intent.getBooleanExtra(FileTransferService.EXTRA_IS_TRANSFERRING, false)

                if (isTransferring) {
                    binding.layoutTransferProgress.visibility = View.VISIBLE
                    binding.progressBar.progress = percentage
                    binding.tvProgressInfo.text = String.format(Locale.getDefault(), "Transferring: %d%% | %.2f MB/s", percentage, speed)
                } else {
                    binding.layoutTransferProgress.visibility = View.GONE
                    Toast.makeText(this@SenderActivity, if (percentage == 100) "Transfer Complete!" else "Transfer Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySenderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        setupUI()
        handleQuickActions()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }

        deviceAdapter = DeviceListAdapter { device -> connectToDevice(device) }
        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = deviceAdapter

        binding.btnSelectFile.setOnClickListener {
            val options = arrayOf("Files/Media", "Installed Apps (APK)")
            AlertDialog.Builder(this)
                .setTitle("Select what to share")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> filePickerLauncher.launch("*/*")
                        1 -> showAppPicker()
                    }
                }.show()
        }

        binding.btnDiscover.setOnClickListener { discoverPeers() }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> { // WiFi Direct
                        binding.viewWifiDirect.visibility = View.VISIBLE
                        binding.viewQrLink.visibility = View.GONE
                    }
                    1 -> { // QR Link
                        binding.viewWifiDirect.visibility = View.GONE
                        if (selectedFileUri != null) {
                            binding.viewQrLink.visibility = View.VISIBLE
                            startHttpServer(selectedFileUri!!)
                        } else {
                            Toast.makeText(this@SenderActivity, "Select a file first", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> { // Bluetooth
                        if (selectedFileUri != null) shareViaBluetooth(selectedFileUri!!)
                        else Toast.makeText(this@SenderActivity, "Select a file first", Toast.LENGTH_SHORT).show()
                    }
                    3 -> { // Share Sheet
                        if (selectedFileUri != null) shareViaAndroidSheet(selectedFileUri!!)
                        else Toast.makeText(this@SenderActivity, "Select a file first", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun handleQuickActions() {
        val quickAction = intent.getStringExtra("QUICK_ACTION")
        when (quickAction) {
            "APPS" -> showAppPicker()
            "MEDIA" -> filePickerLauncher.launch("image/*")
            "AUDIO" -> filePickerLauncher.launch("audio/*")
            "DOCS" -> filePickerLauncher.launch("application/*")
        }
    }

    private fun showAppPicker() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString() }
            .map { info ->
                val file = File(info.publicSourceDir)
                AppItem(
                    info = info,
                    name = pm.getApplicationLabel(info).toString(),
                    size = file.length(),
                    icon = pm.getApplicationIcon(info)
                )
            }

        val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(dialogView)
            .create()

        val rvApps = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_apps)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btn_cancel)
        val btnShare = dialogView.findViewById<android.widget.Button>(R.id.btn_share)
        val tvSelectedCount = dialogView.findViewById<android.widget.TextView>(R.id.tv_selected_count)

        val adapter = AppListAdapter {
            val selectedCount = apps.count { it.isSelected }
            tvSelectedCount.text = "$selectedCount Selected"
            btnShare.isEnabled = selectedCount > 0
        }
        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.adapter = adapter
        adapter.submitList(apps)

        btnShare.isEnabled = false

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnShare.setOnClickListener {
            val selectedApp = apps.firstOrNull { it.isSelected } // Simplification for MVP: share first selected
            if (selectedApp != null) {
                val apkFile = File(selectedApp.info.publicSourceDir)
                selectedFileUri = FileProvider.getUriForFile(this, "$packageName.provider", apkFile)
                binding.tvSelectedFiles.text = "APK selected: ${selectedApp.name}"
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    @SuppressLint("MissingPermission")
    private fun discoverPeers() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@SenderActivity, "Scanning...", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(reasonCode: Int) {
                Toast.makeText(this@SenderActivity, "Scan Failed", Toast.LENGTH_SHORT).show()
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply { deviceAddress = device.deviceAddress }
        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@SenderActivity, "Connecting...", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(reason: Int) {}
        })
    }

    private fun sendFileToConnectedDevice() {
        if (selectedFileUri != null && connectedIp != null) {
            val intent = Intent(this, FileTransferService::class.java).apply {
                action = FileTransferService.ACTION_SEND_FILE
                putExtra(FileTransferService.EXTRA_FILE_URI, selectedFileUri.toString())
                putExtra(FileTransferService.EXTRA_HOST, connectedIp)
                putExtra(FileTransferService.EXTRA_PORT, 8988)
            }
            startService(intent)
        } else {
            Toast.makeText(this, "Wait for connection or select file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startHttpServer(fileUri: Uri) {
        val port = 8080
        if (httpServer == null) {
            httpServer = FileHttpServer(port, applicationContext, fileUri)
            try { httpServer?.start() } catch (e: Exception) { return }
        } else {
            httpServer?.setFileToServe(fileUri)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        val ipStr = String.format(Locale.getDefault(), "%d.%d.%d.%d", ipInt and 0xFF, ipInt shr 8 and 0xFF, ipInt shr 16 and 0xFF, ipInt shr 24 and 0xFF)
        val downloadUrl = "http://$ipStr:$port"

        binding.tvHotspotUrl.text = downloadUrl
        binding.ivQrCode.setImageBitmap(generateQrCode(downloadUrl))
    }

    private fun generateQrCode(text: String): Bitmap? {
        return try {
            val size = 512
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) { null }
    }

    private fun shareViaBluetooth(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = contentResolver.getType(uri) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            setPackage("com.android.bluetooth")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(intent) } catch (e: Exception) {
            intent.setPackage(null)
            startActivity(Intent.createChooser(intent, "Share via Bluetooth"))
        }
    }

    private fun shareViaAndroidSheet(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: "*/*"

        val intent = androidx.core.app.ShareCompat.IntentBuilder(this)
            .setType(mimeType)
            .setStream(uri)
            .setChooserTitle("Share via BEBO Any Share")
            .intent

        intent.clipData = android.content.ClipData.newUri(contentResolver, "File", uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.putExtra(Intent.EXTRA_TITLE, "Share via BEBO Any Share")

        startActivity(Intent.createChooser(intent, "Share via BEBO Any Share"))
    }

    override fun onResume() {
        super.onResume()
        receiver = WifiDirectBroadcastReceiver(wifiP2pManager, channel,
            onPeersChanged = {
                try {
                    @SuppressLint("MissingPermission")
                    val ignored = true
                } catch (e: Exception) {}
                wifiP2pManager.requestPeers(channel) { peers -> deviceAdapter.submitList(peers.deviceList.toList()) }
            },
            onConnectionInfoAvailable = {
                wifiP2pManager.requestConnectionInfo(channel) { info ->
                    if (info.groupFormed) {
                        connectedIp = info.groupOwnerAddress.hostAddress
                        sendFileToConnectedDevice()
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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

    override fun onDestroy() {
        super.onDestroy()
        httpServer?.stop()
    }
}
