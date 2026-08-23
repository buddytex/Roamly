package com.railguard.mobile.perception

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Driver for YDLIDAR X2 using USB Serial communication.
 * Protocol: 115200 baud, 8N1.
 * Optimized for real-time 20 FPS updates.
 */
class YdLidarX2Driver(private val context: Context) {

    private val _scanData = MutableStateFlow(LidarScan())
    val scanData: StateFlow<LidarScan> = _scanData.asStateFlow()

    private val _status = MutableStateFlow("DISCONNECTED")
    val status: StateFlow<String> = _status.asStateFlow()

    private var usbSerialPort: UsbSerialPort? = null
    private var readJob: Job? = null
    private var emitJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isConnected = false
    private val ACTION_USB_PERMISSION = "com.railguard.mobile.USB_PERMISSION"

    // Stats
    private var bytesReadTotal = 0L
    private var packetsTotal = 0L
    private var lastBytesRead = 0L
    private var lastPacketsCount = 0L
    private var lastStatsTime = 0L
    private var pps = 0
    private var bps = 0
    
    // State of the world (one distance per degree)
    private val angleBuffer = FloatArray(360) { 0f }
    
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        Log.d("YdLidarDriver", "USB Permission Granted")
                        connect()
                    } else {
                        Log.d("YdLidarDriver", "USB Permission Denied")
                        _status.value = "PERMISSION DENIED"
                    }
                    
                    if (isReceiverRegistered) {
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {}
                        isReceiverRegistered = false
                    }
                }
            }
        }
    }

    private var isReceiverRegistered = false

    fun connect(): Boolean {
        if (isConnected) return true
        
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

        if (availableDrivers.isEmpty()) {
            _status.value = "NO USB DRIVER FOUND"
            return false
        }

        val driver = availableDrivers.find { it is Cp21xxSerialDriver } 
            ?: availableDrivers.firstOrNull() 
            ?: return false
        
        val device = driver.device
        
        if (!manager.hasPermission(device)) {
            _status.value = "REQUESTING PERMISSION..."
            
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val intent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
            }
            val usbPermissionIntent = PendingIntent.getBroadcast(
                context, 0, intent, flags
            )
            
            if (!isReceiverRegistered) {
                val filter = IntentFilter(ACTION_USB_PERMISSION)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    androidx.core.content.ContextCompat.registerReceiver(context, usbReceiver, filter, 0)
                }
                isReceiverRegistered = true
            }
            
            manager.requestPermission(device, usbPermissionIntent)
            return false
        }

        val connection = manager.openDevice(device) ?: run {
            _status.value = "FAILED TO OPEN DEVICE"
            return false
        }
        
        val port = driver.ports.firstOrNull() ?: run {
            _status.value = "NO PORTS AVAILABLE"
            return false
        }
        
        return try {
            port.open(connection)
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            
            port.dtr = true
            port.rts = true
            
            scope.launch {
                delay(500)
                try {
                    // Start Scan command
                    val startCommand = byteArrayOf(0xA5.toByte(), 0x60.toByte())
                    port.write(startCommand, 100)
                } catch (e: Exception) { e.printStackTrace() }
            }
            
            usbSerialPort = port
            isConnected = true
            bytesReadTotal = 0
            packetsTotal = 0
            lastBytesRead = 0
            lastPacketsCount = 0
            lastStatsTime = System.currentTimeMillis()
            
            _status.value = "CONNECTED"
            startReading()
            startEmitting()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            _status.value = "SERIAL ERROR: ${e.message}"
            false
        }
    }

    fun disconnect() {
        if (!isConnected && !isReceiverRegistered) return
        
        isConnected = false
        _status.value = "DISCONNECTING..."
        
        // 1. Unregister Receiver safely
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver)
            } catch (e: Exception) {}
            isReceiverRegistered = false
        }

        // 2. Shut down communication
        scope.launch {
            try {
                // Cancel jobs
                readJob?.cancel()
                emitJob?.cancel()
                
                // Clear map immediately
                _scanData.value = LidarScan(emptyList(), System.currentTimeMillis())
                
                val port = usbSerialPort
                if (port != null && port.isOpen) {
                    // Try to send STOP command with very short timeout
                    try {
                        port.write(byteArrayOf(0xA5.toByte(), 0x65.toByte()), 20)
                    } catch (e: Exception) {}
                    
                    port.dtr = false
                    port.rts = false
                    port.close()
                }
            } catch (e: Exception) {
                Log.e("YdLidarDriver", "Error during disconnect: ${e.message}")
            } finally {
                usbSerialPort = null
                _status.value = "DISCONNECTED"
            }
        }
    }

    /**
     * Clears all accumulated LiDAR points.
     */
    fun resetData() {
        synchronized(angleBuffer) {
            for (i in 0 until 360) angleBuffer[i] = 0f
        }
        // Emit empty scan immediately
        _scanData.value = LidarScan(emptyList(), System.currentTimeMillis())
    }

    private fun startReading() {
        readJob = scope.launch {
            val buffer = ByteArray(4096)
            val packetBuffer = ByteBuffer.allocate(16384).order(ByteOrder.LITTLE_ENDIAN)

            while (isConnected) {
                try {
                    val port = usbSerialPort ?: break
                    val len = port.read(buffer, 10)
                    if (len > 0) {
                        bytesReadTotal += len
                        packetBuffer.put(buffer, 0, len)
                        parsePackets(packetBuffer)
                    }
                    
                    val now = System.currentTimeMillis()
                    if (now - lastStatsTime >= 1000) {
                        val dt = (now - lastStatsTime) / 1000.0
                        pps = ((packetsTotal - lastPacketsCount) / dt).toInt()
                        bps = ((bytesReadTotal - lastBytesRead) / dt).toInt()
                        
                        lastPacketsCount = packetsTotal
                        lastBytesRead = bytesReadTotal
                        lastStatsTime = now
                        
                        _status.value = "ACTIVE | PPS: $pps | Baud: $bps | Data: ${bytesReadTotal / 1024} KB"
                    }
                } catch (e: Exception) {
                    Log.e("YdLidarX2Driver", "Read error: ${e.message}")
                    delay(50)
                }
            }
        }
    }

    private fun startEmitting() {
        emitJob = scope.launch {
            while (isConnected) {
                emitScan()
                delay(50) // Fixed 20 FPS emission rate
            }
        }
    }

    private fun parsePackets(buffer: ByteBuffer) {
        buffer.flip()
        while (buffer.remaining() >= 10) {
            val pos = buffer.position()
            // Sync Header: 0xAA 0x55
            if (buffer.get(pos) == 0xAA.toByte() && buffer.get(pos + 1) == 0x55.toByte()) {
                val ct = buffer.get(pos + 2).toInt() and 0xFF
                val lsn = buffer.get(pos + 3).toInt() and 0xFF
                val requiredLen = 10 + (lsn * 2)

                if (buffer.remaining() >= requiredLen) {
                    packetsTotal++
                    
                    buffer.position(pos + 4)
                    val fsa = buffer.getShort().toInt() and 0xFFFF
                    val lsa = buffer.getShort().toInt() and 0xFFFF
                    buffer.getShort() // CS

                    // Formula: Angle = (FSA >> 1) / 64.0
                    val startAngle = (fsa shr 1).toFloat() / 64.0f
                    val endAngle = (lsa shr 1).toFloat() / 64.0f
                    
                    val angleDiff = if (endAngle >= startAngle) {
                        endAngle - startAngle
                    } else {
                        (360.0f - startAngle) + endAngle
                    }
                    val angleStep = if (lsn > 1) angleDiff / (lsn - 1) else 0f

                    synchronized(angleBuffer) {
                        for (i in 0 until lsn) {
                            val dist = buffer.getShort().toInt() and 0xFFFF
                            val d = dist.toFloat()
                            val rawAngle = (startAngle + (angleStep * i)) % 360f
                            
                            if (d > 0) {
                                // Angle Correction
                                val correction = Math.toDegrees(Math.atan(21.8 * (155.3 - d) / (155.3 * d))).toFloat()
                                val correctedAngle = (rawAngle + correction + 360f) % 360f
                                
                                // Store in 1-degree bin (overwriting old data)
                                angleBuffer[correctedAngle.toInt() % 360] = d
                            } else {
                                // 0 distance means no reflection/object, clear this bin
                                angleBuffer[rawAngle.toInt() % 360] = 0f
                            }
                        }
                    }
                    buffer.position(pos + requiredLen)
                } else {
                    buffer.position(pos)
                    break
                }
            } else {
                buffer.get() // Skip until sync header
            }
        }
        buffer.compact()
    }

    private fun emitScan() {
        val points = mutableListOf<LidarPoint>()
        synchronized(angleBuffer) {
            for (i in 0 until 360) {
                if (angleBuffer[i] > 0) {
                    points.add(LidarPoint(i.toFloat(), angleBuffer[i]))
                }
            }
        }
        // Force new timestamp to ensure UI recomposition
        _scanData.value = LidarScan(points, System.currentTimeMillis())
    }
}
