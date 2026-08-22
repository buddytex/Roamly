package com.railguard.mobile.perception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.railguard.mobile.profiling.PipelineProfiler
import java.nio.FloatBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Expert RailDetector restored from RailGuard (2) build.
 * Optimized with high-performance aspect-aware preprocessing and profiler hooks.
 */
class RailDetector(context: Context) : AutoCloseable {

    enum class ExecutionProvider { CPU, NNAPI }
    private var currentProvider = ExecutionProvider.CPU

    companion object {
        const val TAG = "RailDetector"
        var MODEL_W = 256
        var MODEL_H = 144
        
        const val PERCEPTION_HORIZON_Y = 0.35f
        const val MIN_TRACK_GAUGE_RATIO = 0.050f
        const val MAX_TRACK_GAUGE_RATIO = 0.500f
        const val MIN_GAUGE_WIDTH_NORM = 0.010f
        const val MAX_GAUGE_WIDTH_NORM = 0.600f
        const val SEARCH_WINDOW_PX = 10
        const val MAX_TRAJECTORY_DEV_PX = 10.0f

        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var inputName: String = "input"

    private var inputBuffer: FloatBuffer? = null
    private var pixelBuffer: IntArray? = null
    private var hmLBuffer: FloatArray? = null
    private var hmRBuffer: FloatArray? = null

    private var lastValidG0 = 0.20f
    private var lastValidG1 = 0.15f
    private var framesSinceUpdate = 999

    init {
        initializeSession(context, ExecutionProvider.CPU) 
    }

    fun setExecutionProvider(context: Context, provider: ExecutionProvider) {
        if (currentProvider == provider && ortSession != null) return
        initializeSession(context, provider)
    }

    private fun initializeSession(context: Context, provider: ExecutionProvider) {
        try {
            ortSession?.close(); ortEnv?.close()
            ortEnv = OrtEnvironment.getEnvironment()
            currentProvider = provider
            
            val modelBytes = context.assets.open("rail_model.onnx").readBytes()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4); setInterOpNumThreads(2)
                addConfigEntry("session.intra_op.allow_spinning", "1")
                addConfigEntry("session.use_device_allocator_for_initializers", "1")
                if (provider == ExecutionProvider.NNAPI) {
                    try { addNnapi(); Log.i(TAG, "NNAPI Active") } catch (e: Exception) { currentProvider = ExecutionProvider.CPU }
                }
            }
            ortSession = ortEnv?.createSession(modelBytes, opts)
            val inputInfo = ortSession?.inputInfo?.entries?.iterator()?.next()
            inputName = inputInfo?.key ?: "input"
            val inputShape = (inputInfo?.value?.info as? ai.onnxruntime.TensorInfo)?.shape
            if (inputShape != null && inputShape.size >= 4) {
                MODEL_H = inputShape[2].toInt().let { if (it <= 0) 144 else it }
                MODEL_W = inputShape[3].toInt().let { if (it <= 0) 256 else it }
            }
        } catch (e: Exception) { Log.e(TAG, "Init failed", e) }
    }

    fun updateProfilerInfo(profiler: PipelineProfiler) {
        profiler.selectedProvider = if (currentProvider == ExecutionProvider.NNAPI) "NNAPI" else "CPU"
        profiler.modelInfo = "rail_model.onnx [1, 3, $MODEL_H, $MODEL_W]"
    }

    data class DetectionResult(
        val primaryTrack: Pair<RailCurve, RailCurve>?,
        val secondaryTracks: List<Pair<RailCurve, RailCurve>> = emptyList(),
        val confidence: Float = 0.0f
    )

    fun detect(imageProxy: ImageProxy, frameIdx: Long, profiler: PipelineProfiler? = null): DetectionResult {
        val session = ortSession ?: return DetectionResult(null)
        val env = ortEnv ?: return DetectionResult(null)

        return try {
            val t0 = android.os.SystemClock.elapsedRealtimeNanos()
            
            // 1, 2, 3: Preprocessing with Correct Scaling (Center Crop)
            val bitmap = imageProxyToScaledBitmap(imageProxy, MODEL_W, MODEL_H, profiler)
                ?: return DetectionResult(null)

            val count = MODEL_H * MODEL_W
            if (pixelBuffer == null || pixelBuffer!!.size != count) {
                pixelBuffer = IntArray(count)
                inputBuffer = ByteBuffer.allocateDirect(1 * 3 * count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                hmLBuffer = FloatArray(count); hmRBuffer = FloatArray(count)
            }
            
            val pb = pixelBuffer!!
            bitmap.getPixels(pb, 0, MODEL_W, 0, 0, MODEL_W, MODEL_H)

            // 4. Normalization (FIXED: removed double division)
            val rScale = 1.0f / (255f * STD[0]); val gScale = 1.0f / (255f * STD[1]); val bScale = 1.0f / (255f * STD[2])
            val rOff = -MEAN[0] / STD[0]; val gOff = -MEAN[1] / STD[1]; val bOff = -MEAN[2] / STD[2]
            
            val ib = inputBuffer!!; ib.clear()
            for (i in 0 until count) {
                val px = pb[i]
                ib.put(i, ((px shr 16) and 0xFF) * rScale + rOff)
                ib.put(i + count, ((px shr 8) and 0xFF) * gScale + gOff)
                ib.put(i + 2 * count, (px and 0xFF) * bScale + bOff)
            }
            ib.rewind()
            val tPrepEnd = android.os.SystemClock.elapsedRealtimeNanos()
            profiler?.record(PipelineProfiler.Stage.NORMALIZATION, (tPrepEnd - t0) / 1e6 * 0.2) // Simplified split

            // 5. Tensor creation
            val tTensor0 = android.os.SystemClock.elapsedRealtimeNanos()
            val shape = longArrayOf(1, 3, MODEL_H.toLong(), MODEL_W.toLong())
            val inputTensor = OnnxTensor.createTensor(env, ib, shape)
            profiler?.record(PipelineProfiler.Stage.TENSOR_CREATION, (android.os.SystemClock.elapsedRealtimeNanos() - tTensor0) / 1e6)

            // 6. ONNX inference
            profiler?.recordInferenceStart(frameIdx)
            val tInf0 = android.os.SystemClock.elapsedRealtimeNanos()
            val results = session.run(mapOf(inputName to inputTensor))
            profiler?.record(PipelineProfiler.Stage.INFERENCE, (android.os.SystemClock.elapsedRealtimeNanos() - tInf0) / 1e6)

            // 7. Heatmap extraction
            val tExt0 = android.os.SystemClock.elapsedRealtimeNanos()
            val rawOutput = results[0].value
            val hmL = hmLBuffer!!; val hmR = hmRBuffer!!
            if (rawOutput is Array<*>) {
                val tensor = if (rawOutput[0] is Array<*>) rawOutput[0] as Array<*> else rawOutput
                val chanL = tensor[0] as Array<FloatArray>; val chanR = tensor[1] as Array<FloatArray>
                for (y in 0 until MODEL_H) { System.arraycopy(chanL[y], 0, hmL, y * MODEL_W, MODEL_W); System.arraycopy(chanR[y], 0, hmR, y * MODEL_W, MODEL_W) }
            } else if (rawOutput is FloatArray) {
                System.arraycopy(rawOutput, 0, hmL, 0, count); System.arraycopy(rawOutput, count, hmR, 0, count)
            }
            profiler?.record(PipelineProfiler.Stage.HEATMAP_EXTRACTION, (android.os.SystemClock.elapsedRealtimeNanos() - tExt0) / 1e6)

            inputTensor.close(); results.close(); bitmap.recycle()

            // 8. Decoder stages
            val tDec0 = android.os.SystemClock.elapsedRealtimeNanos()
            val result = decodeHeatmap(hmL, hmR)
            profiler?.record(PipelineProfiler.Stage.DECODER_TOTAL, (android.os.SystemClock.elapsedRealtimeNanos() - tDec0) / 1e6)

            val totalPoints = (result.primaryTrack?.first?.points?.size ?: 0) + (result.primaryTrack?.second?.points?.size ?: 0)
            profiler?.recordAiMetrics(totalPoints, result.primaryTrack != null, result.secondaryTracks.size, false, false)
            result
        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
            DetectionResult(null)
        }
    }

    private fun imageProxyToScaledBitmap(imageProxy: ImageProxy, targetWidth: Int, targetHeight: Int, profiler: PipelineProfiler?): Bitmap? {
        val t0 = android.os.SystemClock.elapsedRealtimeNanos()
        val bitmap = try { imageProxy.toBitmap() } catch (e: Exception) { return null }
        val t1 = android.os.SystemClock.elapsedRealtimeNanos()
        
        val rotation = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix().apply { 
            if (rotation != 0) postRotate(rotation.toFloat())
            val rw = if (rotation % 180 == 0) bitmap.width else bitmap.height
            val rh = if (rotation % 180 == 0) bitmap.height else bitmap.width
            val scale = max(targetWidth.toFloat() / rw, targetHeight.toFloat() / rh)
            postScale(scale, scale)
        }
        
        val processed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val result = Bitmap.createBitmap(processed, (processed.width - targetWidth) / 2, (processed.height - targetHeight) / 2, targetWidth, targetHeight)
        
        if (processed != result && processed != bitmap) processed.recycle()
        if (bitmap != result) bitmap.recycle()
        
        val t2 = android.os.SystemClock.elapsedRealtimeNanos()
        profiler?.record(PipelineProfiler.Stage.YUV_TO_RGB, (t1 - t0) / 1e6)
        profiler?.record(PipelineProfiler.Stage.RESIZE, (t2 - t1) / 1e6)
        return result
    }

    private fun decodeHeatmap(hmL: FloatArray, hmR: FloatArray): DetectionResult {
        val H = MODEL_H; val W = MODEL_W
        val seeds = findTrackSeeds(hmL, hmR, W, H)
        if (seeds.isEmpty()) return DetectionResult(null)

        val candidates = seeds.mapNotNull { evaluateCandidate(it, hmL, hmR, W, H) }
        val ego = candidates.filter { c -> c.yMax >= 0.35f && c.nearCx in 0.05f..0.95f && (c.yMax - c.yMin) >= 0.05f && c.points >= 6 }.sortedByDescending { it.score }
        val primary = if (ego.isNotEmpty()) { updateGeometricPrior(ego[0]); Pair(ego[0].left, ego[0].right) } else { framesSinceUpdate++; null }
        return DetectionResult(primary, candidates.filter { it !in ego && it.score > 0.05f }.take(2).map { it.left to it.right }, ego.firstOrNull()?.score ?: 0.0f)
    }

    private fun findTrackSeeds(hmL: FloatArray, hmR: FloatArray, W: Int, H: Int): List<TrackSeed> {
        val near = extractSeedsFromBand(hmL, hmR, 0.60f, 0.95f, W, H)
        val mid = extractSeedsFromBand(hmL, hmR, 0.45f, 0.85f, W, H)
        val all = near.toMutableList(); val centers = near.map { (it.lx + it.rx) / (2f * W) }
        for (ms in mid) { if (centers.none { abs(it - (ms.lx + ms.rx) / (2f * W)) < 0.06f }) all.add(ms) }
        return all
    }

    private fun extractSeedsFromBand(hmL: FloatArray, hmR: FloatArray, sY: Float, eY: Float, W: Int, H: Int): List<TrackSeed> {
        val yS = (H * sY).toInt(); val yE = (H * eY).toInt(); val nR = max(1, yE - yS)
        val avgL = FloatArray(W); val avgR = FloatArray(W)
        for (y in yS until yE) { val off = y * W; for (x in 0 until W) { avgL[x] += hmL[off + x]; avgR[x] += hmR[off + x] } }
        for (x in 0 until W) { avgL[x] /= nR.toFloat(); avgR[x] /= nR.toFloat() }
        val pL = findTopKPeaks(avgL, 6, 0.015f, (W * 0.04f).toInt()); val pR = findTopKPeaks(avgR, 6, 0.015f, (W * 0.04f).toInt())
        val seeds = mutableListOf<TrackSeed>()
        for (l in pL) for (r in pR) { if (r.first - l.first in (MIN_TRACK_GAUGE_RATIO * W)..(MAX_TRACK_GAUGE_RATIO * W)) seeds.add(TrackSeed(l.first, r.first, l.second + r.second)) }
        return seeds.sortedByDescending { it.conf }.take(4)
    }

    private fun findTopKPeaks(arr: FloatArray, k: Int, minV: Float, exclW: Int): List<Pair<Float, Float>> {
        val a = arr.copyOf(); val peaks = mutableListOf<Pair<Float, Float>>(); val L = arr.size
        for (iK in 0 until k) {
            var mI = -1; var mV = -1f; for (i in 0 until L) if (a[i] > mV) { mV = a[i]; mI = i }
            if (mI == -1 || mV < minV) break
            val rX = if (mI in 1 until L - 1) { val l = arr[mI - 1]; val c = arr[mI]; val r = arr[mI + 1]; val d = 2f * (l - 2f * c + r); if (abs(d) > 1e-5f) mI + ((l - r) / d).coerceIn(-0.5f, 0.5f) else mI.toFloat() } else mI.toFloat()
            peaks.add(rX to mV); for (i in max(0, mI - exclW) until min(L, mI + exclW + 1)) a[i] = 0f
        }
        return peaks
    }

    private fun evaluateCandidate(seed: TrackSeed, hmL: FloatArray, hmR: FloatArray, W: Int, H: Int): Candidate? {
        val rL = traceRidge(hmL, seed.lx, W, H); val rR = traceRidge(hmR, seed.rx, W, H); if (rL.size < 4 && rR.size < 4) return null
        val sync = synchronizeRails(rL, rR, H) ?: return null; val vI = mutableListOf<Int>()
        for (i in sync.first.indices) if ((sync.second[i].x - sync.first[i].x) in MIN_GAUGE_WIDTH_NORM..MAX_GAUGE_WIDTH_NORM) vI.add(i) else break
        if (vI.size < 6) return null
        val fL = RailCurve(vI.map { sync.first[it] }, seed.conf); val fR = RailCurve(vI.map { sync.second[it] }, seed.conf)
        val yMin = fL.points.last().y; val yMax = fL.points.first().y; val nCx = (fL.points[0].x + fR.points[0].x) / 2f
        val score = 0.4f * max(0f, 1f - 2.8f * abs(nCx - 0.5f)) + 0.4f * (yMax - yMin) / (1.0f - PERCEPTION_HORIZON_Y) + 0.2f * min(1f, fL.points.size / 60f)
        return Candidate(fL, fR, yMin, yMax, nCx, score, fL.points.size)
    }

    private fun traceRidge(hm: FloatArray, sX: Float, W: Int, H: Int): List<RailPoint> {
        val pts = mutableListOf<RailPoint>(); var cX = sX; var dx = 0f; var msd = 0
        for (y in (H - 4) downTo (H * PERCEPTION_HORIZON_Y).toInt()) {
            val pX = cX + dx; val win = if (pts.isEmpty()) 24 else SEARCH_WINDOW_PX
            var mI = -1; var mV = -1f; for (x in max(0, pX.roundToInt() - win) until min(W, pX.roundToInt() + win + 1)) { val v = hm[y * W + x]; if (v > mV) { mV = v; mI = x } }
            if (mV >= 0.004f) {
                msd = 0; val rX = if (mI in 1 until W - 1) { val l = hm[y * W + mI - 1]; val c = hm[y * W + mI]; val r = hm[y * W + mI + 1]; val d = 2f * (l - 2f * c + r); if (abs(d) > 1e-5f) mI + ((l - r) / d).coerceIn(-0.5f, 0.5f) else mI.toFloat() } else mI.toFloat()
                if (pts.size > 2 && abs(rX - pX) > MAX_TRAJECTORY_DEV_PX) { if (++msd > 12) break; cX += dx } else { dx = (rX - cX) * 0.4f + dx * 0.6f; cX = rX }; pts.add(RailPoint(cX / W, y / H.toFloat()))
            } else { if (pts.isEmpty()) continue; if (++msd > 12) break; cX += dx; pts.add(RailPoint(cX / W, y / H.toFloat())) }
        }
        return pts
    }

    private fun synchronizeRails(rL: List<RailPoint>, rR: List<RailPoint>, H: Int): Pair<List<RailPoint>, List<RailPoint>>? {
        if (rL.size >= 6 && rR.size >= 6) {
            val yMax = min(rL[0].y, rR[0].y); val yMin = max(rL.last().y, rR.last().y); if (yMax - yMin < 0.020f) return null
            val cY = mutableListOf<Float>(); var y = yMax; while (y > yMin) { cY.add(y); y -= 1.0f / H }; return cY.map { RailPoint(interpolate(rL, it), it) } to cY.map { RailPoint(interpolate(rR, it), it) }
        }
        val s = if (rL.size >= 12) rL else if (rR.size >= 12) rR else return null; val side = if (rL.size >= 12) "L" else "R"
        val cY = mutableListOf<Float>(); var y = s[0].y; while (y > s.last().y) { cY.add(y); y -= 1.0f / H }
        val sS = cY.map { RailPoint(interpolate(s, it), it) }
        return if (side == "L") sS to cY.mapIndexed { i, curY -> RailPoint(sS[i].x + (lastValidG0 + lastValidG1 * (curY - 0.50f)), curY) }
        else cY.mapIndexed { i, curY -> RailPoint(sS[i].x - (lastValidG0 + lastValidG1 * (curY - 0.50f)), curY) } to sS
    }

    private fun interpolate(pts: List<RailPoint>, y: Float): Float {
        if (pts.size < 2) return pts.firstOrNull()?.x ?: 0.5f; for (i in 0 until pts.size - 1) if (y <= pts[i].y && y >= pts[i + 1].y) { val dY = pts[i].y - pts[i + 1].y; return if (abs(dY) < 1e-5f) pts[i].x else pts[i + 1].x + (y - pts[i + 1].y) / dY * (pts[i].x - pts[i + 1].x) }; return if (y > pts[0].y) pts[0].x else pts.last().x
    }

    private fun updateGeometricPrior(c: Candidate) {
        if (c.points < 10) return; val pl = c.left.points; val pr = c.right.points; val gauges = pr.indices.map { pr[it].x - pl[it].x }; val ys = pl.map { it.y - 0.50f }; var sX = 0f; var sY = 0f; var sXY = 0f; var sX2 = 0f
        for (i in ys.indices) { sX += ys[i]; sY += gauges[i]; sXY += ys[i] * gauges[i]; sX2 += ys[i] * ys[i] }; val d = (ys.size * sX2 - sX * sX); if (abs(d) > 1e-5f) { lastValidG1 = (ys.size * sXY - sX * sY) / d; lastValidG0 = (sY - lastValidG1 * sX) / ys.size; framesSinceUpdate = 0 }
    }

    override fun close() { ortSession?.close(); ortEnv?.close() }
    private data class TrackSeed(val lx: Float, val rx: Float, val conf: Float)
    private data class Candidate(val left: RailCurve, val right: RailCurve, val yMin: Float, val yMax: Float, val nearCx: Float, val score: Float, val points: Int)
}
