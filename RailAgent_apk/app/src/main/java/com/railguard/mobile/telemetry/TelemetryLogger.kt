package com.railguard.mobile.telemetry

import android.content.Context
import android.util.Log
import com.railguard.mobile.perception.AnomalyResult
import com.railguard.mobile.perception.GpsData
import com.railguard.mobile.perception.Orientation
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class TelemetryLogger(private val context: Context) {

    companion object {
        private const val TAG = "TelemetryLogger"
        private const val DIR_NAME = "telemetry_logs"
    }

    private var currentFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    /**
     * Starts a new logging session by creating a new CSV file.
     */
    fun startSession() {
        try {
            val directory = File(context.getExternalFilesDir(null), DIR_NAME)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val fileName = "RailGuard_Log_${dateFormat.format(Date())}.csv"
            currentFile = File(directory, fileName)

            // Write CSV Header
            val header = "Timestamp,Latitude,Longitude,Speed_kmh,Pitch,Roll,Yaw,AI_Confidence,Rail_Status,Anomaly_State,Anomaly_Reason\n"
            FileOutputStream(currentFile, true).use { it.write(header.toByteArray()) }
            
            Log.i(TAG, "Started new logging session: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start logging session", e)
        }
    }

    /**
     * Logs a single telemetry record to the current session file.
     */
    fun log(
        gps: GpsData,
        imu: Orientation,
        aiConfidence: Float,
        railStatus: String,
        anomaly: AnomalyResult?
    ) {
        val file = currentFile ?: return
        
        try {
            val timestamp = System.currentTimeMillis()
            val row = String.format(
                Locale.US,
                "%d,%.6f,%.6f,%.2f,%.2f,%.2f,%.2f,%.2f,%s,%s,%s\n",
                timestamp,
                gps.latitude,
                gps.longitude,
                gps.speed * 3.6f,
                imu.pitch,
                imu.roll,
                imu.yaw,
                aiConfidence,
                railStatus,
                anomaly?.state?.name ?: "NORMAL",
                anomaly?.reason?.name ?: "NONE"
            )
            
            FileOutputStream(file, true).use { it.write(row.toByteArray()) }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing telemetry row", e)
        }
    }

    /**
     * Closes the current session.
     */
    fun endSession() {
        Log.i(TAG, "Closed logging session: ${currentFile?.name}")
        currentFile = null
    }

    /**
     * Returns a list of all saved log files.
     */
    fun getLogFiles(): List<File> {
        val directory = File(context.getExternalFilesDir(null), DIR_NAME)
        if (!directory.exists()) return emptyList()
        return directory.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Deletes all saved log files.
     */
    fun clearLogs() {
        val directory = File(context.getExternalFilesDir(null), DIR_NAME)
        directory.listFiles()?.forEach { it.delete() }
    }
}
