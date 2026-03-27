package com.abanoub.beboanyshare

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID

class FileTransferService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var historyManager: HistoryManager
    private var serverSocket: ServerSocket? = null
    private var isReceiving = false

    override fun onCreate() {
        super.onCreate()
        historyManager = HistoryManager(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_SEND_FILE -> {
                val fileUriString = intent.getStringExtra(EXTRA_FILE_URI)
                val host = intent.getStringExtra(EXTRA_HOST)
                val port = intent.getIntExtra(EXTRA_PORT, 8988)

                if (fileUriString != null && host != null) {
                    sendFile(Uri.parse(fileUriString), host, port)
                }
            }
            ACTION_RECEIVE_FILE -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8988)
                startReceiving(port)
            }
        }
        return START_NOT_STICKY
    }

    private fun startReceiving(port: Int) {
        if (isReceiving) return
        isReceiving = true

        serviceScope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "ServerSocket listening on port $port")
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    Log.d(TAG, "Client connected for receiving")
                    handleClient(client)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error", e)
            } finally {
                serverSocket?.close()
                serverSocket = null
                isReceiving = false
            }
        }
    }

    private fun handleClient(client: Socket) {
        serviceScope.launch {
            try {
                val dataInputStream = DataInputStream(BufferedInputStream(client.getInputStream()))

                // Read metadata
                val rawFileName = dataInputStream.readUTF()
                val fileSize = dataInputStream.readLong()

                // Security Fix: Prevent Path Traversal
                val sanitizedFileName = File(rawFileName).name
                val finalFileName = if (sanitizedFileName.isEmpty() || sanitizedFileName == "." || sanitizedFileName == "..") {
                    "received_file_${System.currentTimeMillis()}"
                } else {
                    sanitizedFileName
                }

                val receivedFile = File(getExternalFilesDir(null), finalFileName)
                val outputStream = BufferedOutputStream(FileOutputStream(receivedFile))

                val buffer = ByteArray(64 * 1024) // 64KB chunk
                var len: Int = 0
                var totalBytesReceived = 0L
                val startTime = System.currentTimeMillis()

                Log.d(TAG, "Starting to receive file: $finalFileName, size: $fileSize")

                while (totalBytesReceived < fileSize && dataInputStream.read(buffer, 0, minOf(buffer.size.toLong(), fileSize - totalBytesReceived).toInt()).also { len = it } != -1) {
                    outputStream.write(buffer, 0, len)
                    totalBytesReceived += len

                    val now = System.currentTimeMillis()
                    val timeElapsedSec = (now - startTime) / 1000.0
                    val speedMbPs = if (timeElapsedSec > 0) (totalBytesReceived / (1024.0 * 1024.0)) / timeElapsedSec else 0.0
                    val percentage = if (fileSize > 0) ((totalBytesReceived * 100) / fileSize).toInt() else 0
                    val remainingBytes = fileSize - totalBytesReceived
                    val timeRemainingSec = if (speedMbPs > 0) (remainingBytes / (1024.0 * 1024.0)) / speedMbPs else 0.0

                    broadcastProgress(percentage, speedMbPs, timeRemainingSec.toLong())
                }

                outputStream.flush()
                outputStream.close()

                // Read original MD5
                val originalMd5 = dataInputStream.readUTF()
                val receivedMd5 = calculateMD5(receivedFile)

                val isSuccess = originalMd5 == receivedMd5
                Log.d(TAG, "Receive finished. Integrity check: ${if(isSuccess) "PASS" else "FAIL"}")

                historyManager.addRecord(
                    TransferRecord(
                        id = UUID.randomUUID().toString(),
                        fileName = finalFileName,
                        filePath = receivedFile.absolutePath,
                        fileSize = totalBytesReceived,
                        timestamp = System.currentTimeMillis(),
                        isIncoming = true,
                        isSuccess = isSuccess
                    )
                )

                broadcastProgress(100, 0.0, 0, false)

            } catch (e: Exception) {
                Log.e(TAG, "Error receiving file", e)
            } finally {
                client.close()
            }
        }
    }

    private fun sendFile(fileUri: Uri, host: String, port: Int) {
        serviceScope.launch {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.bind(null)
                socket.connect(InetSocketAddress(host, port), 10000)

                val dataOutputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val inputStream: InputStream? = contentResolver.openInputStream(fileUri)

                if (inputStream != null) {
                    val fileName = getFileName(fileUri) ?: "unknown_file"
                    val fileSize = getFileSize(fileUri)

                    // Send metadata
                    dataOutputStream.writeUTF(fileName)
                    dataOutputStream.writeLong(fileSize)
                    dataOutputStream.flush()

                    val bufferedInput = BufferedInputStream(inputStream)
                    val md = MessageDigest.getInstance("MD5")
                    val buffer = ByteArray(64 * 1024)
                    var len: Int = 0
                    var totalBytesTransferred = 0L
                    val startTime = System.currentTimeMillis()

                    while (bufferedInput.read(buffer).also { len = it } != -1) {
                        md.update(buffer, 0, len)
                        dataOutputStream.write(buffer, 0, len)
                        totalBytesTransferred += len

                        val now = System.currentTimeMillis()
                        val timeElapsedSec = (now - startTime) / 1000.0
                        val speedMbPs = if (timeElapsedSec > 0) (totalBytesTransferred / (1024.0 * 1024.0)) / timeElapsedSec else 0.0
                        val percentage = if (fileSize > 0) ((totalBytesTransferred * 100) / fileSize).toInt() else 0
                        val remainingBytes = fileSize - totalBytesTransferred
                        val timeRemainingSec = if (speedMbPs > 0) (remainingBytes / (1024.0 * 1024.0)) / speedMbPs else 0.0

                        broadcastProgress(percentage, speedMbPs, timeRemainingSec.toLong())
                    }

                    dataOutputStream.flush()

                    // Send MD5
                    val mdbytes = md.digest()
                    val sb = StringBuilder()
                    for (i in mdbytes.indices) {
                        sb.append(Integer.toString((mdbytes[i].toInt() and 0xff) + 0x100, 16).substring(1))
                    }
                    val originalMd5 = sb.toString()
                    dataOutputStream.writeUTF(originalMd5)
                    dataOutputStream.flush()

                    bufferedInput.close()

                    historyManager.addRecord(
                        TransferRecord(
                            id = UUID.randomUUID().toString(),
                            fileName = fileName,
                            filePath = "", // Sender might not need local path
                            fileSize = totalBytesTransferred,
                            timestamp = System.currentTimeMillis(),
                            isIncoming = false,
                            isSuccess = true
                        )
                    )

                    broadcastProgress(100, 0.0, 0, false)
                    Log.d(TAG, "File sent successfully")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending file", e)
                broadcastProgress(0, 0.0, 0, false)
            } finally {
                if (socket?.isConnected == true) {
                    try {
                        socket.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun broadcastProgress(percentage: Int, speed: Double, timeRemaining: Long, isTransferring: Boolean = true) {
        val intent = Intent(ACTION_TRANSFER_PROGRESS)
        intent.putExtra(EXTRA_PROGRESS, percentage)
        intent.putExtra(EXTRA_SPEED, speed)
        intent.putExtra(EXTRA_TIME_REMAINING, timeRemaining)
        intent.putExtra(EXTRA_IS_TRANSFERRING, isTransferring)
        sendBroadcast(intent)
    }

    @Suppress("Range")
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    @Suppress("Range")
    private fun getFileSize(uri: Uri): Long {
        var size = 0L
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
                }
            } finally {
                cursor?.close()
            }
        }
        return size
    }

    private fun calculateMD5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        val fis = FileInputStream(file)
        val dataBytes = ByteArray(1024)
        var nread = 0
        while (fis.read(dataBytes).also { nread = it } != -1) {
            md.update(dataBytes, 0, nread)
        }
        val mdbytes = md.digest()
        val sb = StringBuilder()
        for (i in mdbytes.indices) {
            sb.append(Integer.toString((mdbytes[i].toInt() and 0xff) + 0x100, 16).substring(1))
        }
        return sb.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        serverSocket?.close()
        serviceJob.cancel()
    }

    companion object {
        private const val TAG = "FileTransferService"
        const val ACTION_SEND_FILE = "com.swiftshare.SEND_FILE"
        const val ACTION_RECEIVE_FILE = "com.abanoub.beboanyshare.RECEIVE_FILE"
        const val ACTION_TRANSFER_PROGRESS = "com.swiftshare.TRANSFER_PROGRESS"

        const val EXTRA_FILE_URI = "file_uri"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"

        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_TIME_REMAINING = "time_remaining"
        const val EXTRA_IS_TRANSFERRING = "is_transferring"
    }
}
