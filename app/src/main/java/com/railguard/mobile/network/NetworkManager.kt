package com.railguard.mobile.network

import android.content.Context
import android.provider.Settings
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages networking with the RailGuard Pi server.
 */
class NetworkManager(private val context: Context) {

    private var currentBaseUrl = "http://10.240.194.204:8000/"
    private var _apiService: RailGuardApiService? = null

    fun setBaseUrl(ip: String) {
        currentBaseUrl = if (ip.startsWith("http")) ip else "http://$ip:8000/"
        _apiService = null 
    }

    fun getBaseUrl(): String = currentBaseUrl

    companion object {
        fun getLocalIpAddress(): String? {
            try {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }
    }

    private val _isServerOnline = MutableStateFlow(false)
    val isServerOnline: StateFlow<Boolean> = _isServerOnline.asStateFlow()

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    private val apiService: RailGuardApiService
        get() {
            if (_apiService == null) {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }

                val client = OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()

                _apiService = Retrofit.Builder()
                    .baseUrl(currentBaseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                    .create(RailGuardApiService::class.java)
            }
            return _apiService!!
        }

    /**
     * Sends a heartbeat with current status and GPS.
     * Returns the response from the server which may contain commands.
     */
    suspend fun sendHeartbeat(status: String, gps: NetworkGps?): PiResponse? {
        return try {
            val request = HeartbeatRequest(
                deviceId = deviceId,
                status = status,
                gps = gps
            )
            val response = apiService.sendHeartbeat(request)
            val isSuccess = response.isSuccessful
            _isServerOnline.value = isSuccess
            
            if (isSuccess) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            _isServerOnline.value = false
            e.printStackTrace()
            null
        }
    }

    /**
     * Logs an inspection event to the server.
     */
    suspend fun logEvent(
        type: String, 
        severity: String, 
        message: String, 
        gps: NetworkGps?, 
        metadata: Map<String, String> = emptyMap()
    ) {
        try {
            val request = EventRequest(
                deviceId = deviceId,
                type = type,
                severity = severity,
                message = message,
                gps = gps,
                metadata = metadata
            )
            val response = apiService.logEvent(request)
            _isServerOnline.value = response.isSuccessful
        } catch (e: Exception) {
            _isServerOnline.value = false
            e.printStackTrace()
        }
    }

    /**
     * Uploads an image captured during an anomaly.
     */
    suspend fun uploadAnomalyImage(imageFile: File, eventId: String? = null) {
        try {
            val deviceIdBody = deviceId.toRequestBody("text/plain".toMediaTypeOrNull())
            val eventIdBody = eventId?.toRequestBody("text/plain".toMediaTypeOrNull())
            
            val requestFile = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)

            val response = apiService.uploadImage(deviceIdBody, eventIdBody, body)
            _isServerOnline.value = response.isSuccessful
        } catch (e: Exception) {
            _isServerOnline.value = false
            e.printStackTrace()
        }
    }

    /**
     * Tests the connection to the server and returns a descriptive status/error.
     */
    suspend fun testConnection(): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val phoneIp = getLocalIpAddress() ?: "Unknown"
        val targetIp = currentBaseUrl.substringAfter("//").substringBefore(":")
        val port = 8000
        
        val sb = StringBuilder()
        sb.append("[$timestamp] DEEP DIAGNOSTICS\n")
        sb.append("Phone IP: $phoneIp\n")
        sb.append("Target: $targetIp:$port\n\n")
        
        // 1. Network Interface Check
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isWifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
            sb.append("Transport: ${if (isWifi) "WIFI" else ""}${if (isCellular) " + CELLULAR" else ""}\n")
            sb.append("Metered: ${caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false}\n")
        } catch (e: Exception) {
            sb.append("Net Check Error: ${e.message}\n")
        }

        // 2. Internet Reachability (Google DNS)
        try {
            val googleSocket = java.net.Socket()
            googleSocket.connect(java.net.InetSocketAddress("8.8.8.8", 53), 1500)
            googleSocket.close()
            sb.append("Google (8.8.8.8): REACHABLE\n")
        } catch (e: Exception) {
            sb.append("Google (8.8.8.8): FAILED (${e.javaClass.simpleName})\n")
        }

        // 2b. ICMP Ping Test (Low level)
        try {
            val process = Runtime.getRuntime().exec("ping -c 1 -W 2 $targetIp")
            val exitCode = process.waitFor()
            sb.append("ICMP Ping: ${if (exitCode == 0) "REPLY ✓" else "NO REPLY ✗ ($exitCode)"}\n")
        } catch (e: Exception) {
            sb.append("ICMP Ping: ERROR (${e.message})\n")
        }
        
        // 3. Pi Raw Socket Test
        sb.append("Pi Socket: ")
        try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(targetIp, port), 3000)
            socket.close()
            sb.append("OPEN ✓\n")
            
            // 4. OkHttp Diagnostic
            try {
                val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).build()
                val request = okhttp3.Request.Builder().url(currentBaseUrl).build()
                val response = client.newCall(request).execute()
                sb.append("HTTP Status: ${response.code}\n")
                _isServerOnline.value = true
                sb.append("\nRESULT: SUCCESS")
            } catch (e: Exception) {
                sb.append("HTTP ERROR: ${e.javaClass.simpleName} - ${e.message}\n")
            }
        } catch (e: java.net.SocketTimeoutException) {
            _isServerOnline.value = false
            sb.append("TIMEOUT ✗\n")
            sb.append("Error Details: ${e.message}\n")
            if (phoneIp.substringBeforeLast(".") != targetIp.substringBeforeLast(".")) {
                sb.append("\nCRITICAL: Subnet Mismatch! Phone is on ${phoneIp.substringBeforeLast(".")}.x but Pi is on ${targetIp.substringBeforeLast(".")}.x")
            } else {
                sb.append("\nTIP: Browser works but App doesn't. This is 99% GrapheneOS 'Local Network' isolation or a router blocking App sockets.")
            }
        } catch (e: java.net.ConnectException) {
            _isServerOnline.value = false
            sb.append("REFUSED ✗\n(Is the Pi server script running?)\n")
        } catch (e: Exception) {
            _isServerOnline.value = false
            sb.append("FAILED: ${e.javaClass.simpleName}\n")
        }
        
        sb.toString()
    }
}
