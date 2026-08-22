package com.railguard.mobile.profiling

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Expert-level Engineering Profiler for RailGuard AI.
 * Tracks 13+ timing stages, AI stability, and hardware state with full statistical distribution.
 */
class PipelineProfiler {

    companion object {
        private const val TAG = "RailGuardProfile"
        private const val REPORT_INTERVAL_FRAMES = 1
        private const val MAX_SAMPLES = 300
    }

    enum class Stage {
        ACQUISITION,
        YUV_TO_RGB,
        RESIZE,
        NORMALIZATION,
        TENSOR_CREATION,
        INFERENCE,
        HEATMAP_EXTRACTION,
        DECODER_TOTAL,
        TRACK_PAIRING,
        TRACK_ASSOCIATION,
        EMA_SMOOTHING,
        OVERLAY_RENDERING,
        TOTAL_PIPELINE
    }

    // Timing metrics
    private val samples = ConcurrentHashMap<Stage, MutableList<Double>>()
    
    // AI Stability metrics
    private val railPointCounts = mutableListOf<Int>()
    private var primaryDetections = 0
    private var secondaryDetections = 0
    private var frameCount = 0
    private var trackJumps = 0
    private var trackDisappearances = 0
    
    // Frame Age tracking
    private val frameCaptureTimes = ConcurrentHashMap<Long, Long>()
    private val frameAgeAtInference = mutableListOf<Double>()
    private val frameAgeAtDisplay = mutableListOf<Double>()

    // Metadata
    var deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
    var androidVersion = Build.VERSION.RELEASE
    var chipset = Build.HARDWARE
    var cameraSpec = "Unknown"
    var modelInfo = "rail_model.onnx [1, 3, 288, 512]"
    var availableProviders = emptyList<String>()
    var selectedProvider = "Unknown"
    var cpuThreadCount = 4

    var lastGeneratedReport by mutableStateOf("Initializing Profiler... Waiting for first frame.")
        private set

    private var sessionStartTime = System.currentTimeMillis()
    private var lastReportTime = System.currentTimeMillis()

    fun record(stage: Stage, durationMs: Double) {
        val list = samples.getOrPut(stage) { Collections.synchronizedList(mutableListOf()) }
        synchronized(list) {
            list.add(durationMs)
            if (list.size > MAX_SAMPLES) list.removeAt(0)
        }
    }

    fun startFrame(frameIdx: Long, captureTimestampNs: Long) {
        frameCaptureTimes[frameIdx] = captureTimestampNs
    }

    fun recordInferenceStart(frameIdx: Long) {
        val captureNs = frameCaptureTimes[frameIdx] ?: return
        val ageMs = (android.os.SystemClock.elapsedRealtimeNanos() - captureNs) / 1e6
        frameAgeAtInference.add(ageMs)
        if (frameAgeAtInference.size > MAX_SAMPLES) frameAgeAtInference.removeAt(0)
    }

    fun recordAiMetrics(points: Int, hasPrimary: Boolean, secondaryCount: Int, jumped: Boolean, disappeared: Boolean) {
        railPointCounts.add(points)
        if (railPointCounts.size > MAX_SAMPLES) railPointCounts.removeAt(0)
        if (hasPrimary) primaryDetections++
        secondaryDetections += secondaryCount
        if (jumped) trackJumps++
        if (disappeared) trackDisappearances++
    }

    fun endFrame(frameIdx: Long, context: Context? = null) {
        val captureNs = frameCaptureTimes.remove(frameIdx)
        if (captureNs != null) {
            val ageMs = (android.os.SystemClock.elapsedRealtimeNanos() - captureNs) / 1e6
            frameAgeAtDisplay.add(ageMs)
            if (frameAgeAtDisplay.size > MAX_SAMPLES) frameAgeAtDisplay.removeAt(0)
        }
        
        frameCount++
        if (frameCount % REPORT_INTERVAL_FRAMES == 0) {
            val report = generateReport(context)
            lastGeneratedReport = report
            Log.i(TAG, report)
            if (context != null) saveReportToFile(context, report)
        }
    }

    private fun generateReport(context: Context?): String {
        val now = System.currentTimeMillis()
        val intervalMs = now - lastReportTime
        val effectiveFps = (REPORT_INTERVAL_FRAMES.toDouble() / (intervalMs / 1000.0))
        val instantFps = 1000.0 / (samples[Stage.TOTAL_PIPELINE]?.lastOrNull() ?: 1.0)
        lastReportTime = now

        val batteryLevel = context?.let { ctx ->
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } ?: -1

        val sb = StringBuilder()
        sb.append("\n============================================================\n")
        sb.append("RAILGUARD AI PERFORMANCE PROFILE\n")
        sb.append("============================================================\n")
        sb.append("Frame: $frameCount\n\n")
        
        val stages = listOf(
            Stage.ACQUISITION to "Camera acquisition",
            Stage.YUV_TO_RGB to "YUV → RGB",
            Stage.RESIZE to "Resize",
            Stage.NORMALIZATION to "Normalization",
            Stage.TENSOR_CREATION to "Tensor creation"
        )
        
        stages.forEach { (stage, label) ->
            val data = samples[stage] ?: return@forEach
            sb.append(String.format(Locale.US, "%-25s %7.1f ms\n", "$label:", data.lastOrNull() ?: 0.0))
        }

        sb.append(String.format(Locale.US, "\n%-25s %7.1f ms\n", "ONNX inference:", samples[Stage.INFERENCE]?.lastOrNull() ?: 0.0))

        val decoderStages = listOf(
            Stage.HEATMAP_EXTRACTION to "Heatmap extraction",
            Stage.DECODER_TOTAL to "Heatmap decode",
            Stage.TRACK_PAIRING to "Track pairing",
            Stage.TRACK_ASSOCIATION to "Track association",
            Stage.EMA_SMOOTHING to "EMA smoothing"
        )
        
        sb.append("\n")
        decoderStages.forEach { (stage, label) ->
            val data = samples[stage] ?: return@forEach
            sb.append(String.format(Locale.US, "%-25s %7.1f ms\n", "$label:", data.lastOrNull() ?: 0.0))
        }

        sb.append(String.format(Locale.US, "\n%-25s %7.1f ms\n", "Overlay rendering:", samples[Stage.OVERLAY_RENDERING]?.lastOrNull() ?: 0.0))
        sb.append(String.format(Locale.US, "\n%-25s %7.1f ms\n", "TOTAL PIPELINE:", samples[Stage.TOTAL_PIPELINE]?.lastOrNull() ?: 0.0))

        sb.append(String.format(Locale.US, "\n%-25s %7.1f\n", "Instant FPS:", instantFps))
        sb.append(String.format(Locale.US, "%-25s %7.1f\n", "Effective FPS:", effectiveFps))
        
        sb.append("\n------------------------------------------------------------\n")
        sb.append("DETAILED STATISTICS (Last $MAX_SAMPLES frames)\n")
        sb.append(String.format(Locale.US, "%-18s | %-5s | %-5s | %-5s | %-5s | %-5s\n", "Stage", "Mean", "Med", "P90", "P95", "Max"))
        
        Stage.entries.forEach { stage ->
            val data = samples[stage] ?: return@forEach
            val stats = calculateStats(data)
            sb.append(String.format(Locale.US, "%-18s | %5.1f | %5.1f | %5.1f | %5.1f | %5.1f\n", 
                stage.name, stats.mean, stats.median, stats.p90, stats.p95, stats.max))
        }

        val infAge = calculateStats(frameAgeAtInference)
        val dispAge = calculateStats(frameAgeAtDisplay)
        
        sb.append("------------------------------------------------------------\n")
        sb.append("LATENCY & QUEUEING ANALYSIS\n")
        sb.append(String.format(Locale.US, "Frame Age @ Inference: Mean: %5.1f | P95: %5.1f ms\n", infAge.mean, infAge.p95))
        sb.append(String.format(Locale.US, "Frame Age @ Display  : Mean: %5.1f | P95: %5.1f ms\n", dispAge.mean, dispAge.p95))
        
        val ageTrend = if (frameAgeAtDisplay.size >= 20) {
            val startAge = frameAgeAtDisplay.take(10).average()
            val endAge = frameAgeAtDisplay.takeLast(10).average()
            if (endAge > startAge + 50) "INCREASING (Queueing Detected!)" else "STABLE"
        } else "COLLECTING"
        sb.append("Queue Status         : $ageTrend\n")

        sb.append("------------------------------------------------------------\n")
        sb.append("ONNX RUNTIME\n")
        sb.append("Selected Provider    : $selectedProvider\n")
        sb.append("Available Providers  : ${availableProviders.joinToString(", ")}\n")
        sb.append("Model                : $modelInfo\n")
        sb.append("============================================================\n")
        
        return sb.toString()
    }

    private fun saveReportToFile(context: Context, report: String) {
        try {
            val fileName = "baseline_${selectedProvider.lowercase(Locale.US)}.txt"
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file, false).use { it.write(report.toByteArray()) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save profile", e)
        }
    }

    private data class Stats(val mean: Double, val median: Double, val p90: Double, val p95: Double, val max: Double)

    private fun calculateStats(data: List<Double>): Stats {
        if (data.isEmpty()) return Stats(0.0, 0.0, 0.0, 0.0, 0.0)
        val sorted = data.sorted()
        return Stats(
            data.average(),
            sorted[sorted.size / 2],
            sorted[(sorted.size * 0.90).toInt().coerceAtMost(sorted.size - 1)],
            sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)],
            sorted.last()
        )
    }
}
