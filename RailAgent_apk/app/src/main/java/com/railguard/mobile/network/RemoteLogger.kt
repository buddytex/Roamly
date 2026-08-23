package com.railguard.mobile.network

import android.util.Log
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * High-performance remote log streamer.
 * Sends profiling reports and logs to a remote development laptop via HTTP POST or UDP.
 */
object RemoteLogger {
    private const val TAG = "RemoteLogger"
    private const val MAX_UDP_PAYLOAD = 1400

    private var socket: DatagramSocket? = null
    private var targetAddress: InetAddress? = null
    var targetPort: Int = 9999
    private val scope = CoroutineScope(Dispatchers.IO)

    var isEnabled: Boolean = false
    var laptopIp: String = "127.0.0.1"
        set(value) {
            field = value.trim()
            updateAddress()
        }

    init {
        updateAddress()
    }

    private fun updateAddress() {
        scope.launch {
            try {
                val ip = laptopIp.trim()
                if (ip.isNotEmpty()) {
                    targetAddress = InetAddress.getByName(ip)
                    reinitUdpSocket()
                    Log.d(TAG, "Target address resolved to: $targetAddress:$targetPort")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve address for $laptopIp: ${e.message}")
            }
        }
    }

    private fun reinitUdpSocket() {
        try {
            socket?.close()
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not initialize UDP socket: ${e.message}")
        }
    }

    /**
     * Sends a test packet immediately via HTTP/UDP and returns status description.
     */
    suspend fun sendTestPacket(): String = withContext(Dispatchers.IO) {
        val ip = laptopIp.trim()
        if (ip.isEmpty()) {
            return@withContext "ERROR: Laptop IP is empty"
        }

        val testMsg = "[TEST PACKET] RailGuard connected at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}\n"

        // 1. Try HTTP first (Bypasses Android kernel EPERM UDP restrictions)
        val httpResult = sendHttpLog(ip, targetPort, testMsg)
        if (httpResult.first) {
            return@withContext "SUCCESS: HTTP 200 OK (Connected to $ip:$targetPort)"
        }

        // 2. Fallback to UDP
        try {
            val addr = InetAddress.getByName(ip)
            if (socket == null || socket?.isClosed == true) {
                reinitUdpSocket()
            }

            val bytes = testMsg.toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(bytes, bytes.size, addr, targetPort)
            socket?.send(packet)
            Log.i(TAG, "Sent test UDP packet to $addr:$targetPort")
            "SUCCESS: UDP sent to $ip:$targetPort (HTTP: ${httpResult.second})"
        } catch (e: Exception) {
            Log.e(TAG, "Error sending test packet", e)
            "FAILED: HTTP: ${httpResult.second} | UDP: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * Streams a profiling report to the laptop.
     */
    fun stream(message: String) {
        if (!isEnabled) return
        val ip = laptopIp.trim()
        if (ip.isEmpty()) return

        scope.launch {
            // Attempt HTTP POST first for guaranteed delivery without EPERM
            val httpSuccess = sendHttpLog(ip, targetPort, message).first
            if (!httpSuccess) {
                // If HTTP failed, try UDP as secondary
                try {
                    var addr = targetAddress ?: InetAddress.getByName(ip).also { targetAddress = it }
                    if (socket == null || socket?.isClosed == true) {
                        reinitUdpSocket()
                    }

                    val allBytes = message.toByteArray(Charsets.UTF_8)
                    if (allBytes.size <= MAX_UDP_PAYLOAD) {
                        val packet = DatagramPacket(allBytes, allBytes.size, addr, targetPort)
                        socket?.send(packet)
                    } else {
                        var offset = 0
                        while (offset < allBytes.size) {
                            val chunkSize = minOf(MAX_UDP_PAYLOAD, allBytes.size - offset)
                            val packet = DatagramPacket(allBytes, offset, chunkSize, addr, targetPort)
                            socket?.send(packet)
                            offset += chunkSize
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Stream error: ${e.message}")
                }
            }
        }
    }

    private fun sendHttpLog(ip: String, port: Int, message: String): Pair<Boolean, String> {
        return try {
            val url = URL("http://$ip:$port/log")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 1500
                readTimeout = 1500
                doOutput = true
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            }
            conn.outputStream.use { os: OutputStream ->
                os.write(message.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                Pair(true, "HTTP $code")
            } else {
                Pair(false, "HTTP $code")
            }
        } catch (e: Exception) {
            Pair(false, "${e.javaClass.simpleName}: ${e.message}")
        }
    }
}


