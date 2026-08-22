package com.railguard.mobile.perception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * RailDetector runs the MobileNetV3-Small ONNX model on camera frames.
 *
 * Model Specifications:
 * - Input:  Float32 Tensor of shape (1, 3, 144, 256) (CHW format, ImageNet normalized)
 * - Output: Float32 Tensor of shape (1, 64)
 *     - 0..15:  Left rail X coordinates (normalized 0.0 - 1.0)
 *     - 16..31: Right rail X coordinates (normalized 0.0 - 1.0)
 *     - 32..47: Left rail Y coordinates (normalized 0.0 - 1.0)
 *     - 48..63: Right rail Y coordinates (normalized 0.0 - 1.0)
 */
class RailDetector(context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "RailDetector"
        const val MODEL_FILE_NAME = "rail_model.onnx"
        const val INPUT_WIDTH = 256
        const val INPUT_HEIGHT = 144
        const val NUM_POINTS = 16

        // ImageNet Normalization Constants
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    // Pre-allocated buffers
    private val floatBuffer: FloatBuffer
    private val pixelsArray = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
    private var frameCount = 0

    init {
        val modelBytes = context.assets.open(MODEL_FILE_NAME).use { it.readBytes() }
        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
        }
        session = ortEnv.createSession(modelBytes, sessionOptions)

        val byteBuffer = ByteBuffer.allocateDirect(1 * 3 * INPUT_HEIGHT * INPUT_WIDTH * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        floatBuffer = byteBuffer.asFloatBuffer()
        Log.i(TAG, "RailDetector initialized successfully with model: $MODEL_FILE_NAME (${modelBytes.size} bytes)")
    }

    /**
     * Runs inference on an ImageProxy from CameraX.
     */
    @Synchronized
    fun detect(imageProxy: ImageProxy): Pair<RailCurve, RailCurve>? {
        val bitmap = imageProxyToBitmap(imageProxy) ?: return null
        return detect(bitmap)
    }

    /**
     * Runs inference on a Bitmap matching the exact Python inference pipeline.
     */
    @Synchronized
    fun detect(bitmap: Bitmap): Pair<RailCurve, RailCurve>? {
        try {
            // Resize directly to 256x144 (exact same as Python cv2.resize / PIL.resize)
            val scaledBitmap = if (bitmap.width == INPUT_WIDTH && bitmap.height == INPUT_HEIGHT) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, INPUT_WIDTH, INPUT_HEIGHT, true)
            }

            // Extract ARGB pixels
            scaledBitmap.getPixels(pixelsArray, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)

            // Fill planar CHW float buffer
            floatBuffer.rewind()
            val planeSize = INPUT_WIDTH * INPUT_HEIGHT

            // Red channel
            for (i in 0 until planeSize) {
                val color = pixelsArray[i]
                val r = ((color shr 16) and 0xFF) / 255.0f
                floatBuffer.put(i, (r - MEAN[0]) / STD[0])
            }

            // Green channel
            for (i in 0 until planeSize) {
                val color = pixelsArray[i]
                val g = ((color shr 8) and 0xFF) / 255.0f
                floatBuffer.put(planeSize + i, (g - MEAN[1]) / STD[1])
            }

            // Blue channel
            for (i in 0 until planeSize) {
                val color = pixelsArray[i]
                val b = (color and 0xFF) / 255.0f
                floatBuffer.put(2 * planeSize + i, (b - MEAN[2]) / STD[2])
            }

            floatBuffer.rewind()

            // Run ONNX Model
            val shape = longArrayOf(1, 3, INPUT_HEIGHT.toLong(), INPUT_WIDTH.toLong())
            val inputTensor = OnnxTensor.createTensor(ortEnv, floatBuffer, shape)

            val results = session.run(mapOf("image" to inputTensor))
            inputTensor.close()

            val outputTensor = results[0] as OnnxTensor
            val rawOutput = (outputTensor.value as Array<FloatArray>)[0]
            results.close()

            // Parse output coordinates
            val leftPoints = ArrayList<RailPoint>(NUM_POINTS)
            val rightPoints = ArrayList<RailPoint>(NUM_POINTS)

            for (i in 0 until NUM_POINTS) {
                val lx = rawOutput[i].coerceIn(0.0f, 1.0f)
                val ly = rawOutput[32 + i].coerceIn(0.0f, 1.0f)
                leftPoints.add(RailPoint(lx, ly))

                val rx = rawOutput[16 + i].coerceIn(0.0f, 1.0f)
                val ry = rawOutput[48 + i].coerceIn(0.0f, 1.0f)
                rightPoints.add(RailPoint(rx, ry))
            }

            // Calculate confidence score based on geometry ordering and spread
            var validOrderingCount = 0
            for (i in 0 until NUM_POINTS) {
                if (leftPoints[i].x < rightPoints[i].x) {
                    validOrderingCount++
                }
            }
            val orderingConfidence = (validOrderingCount.toFloat() / NUM_POINTS).coerceIn(0.0f, 1.0f)

            frameCount++
            if (frameCount % 30 == 0) {
                Log.d(
                    TAG,
                    "Frame #$frameCount -> L_x[0]=%.3f L_x[15]=%.3f | R_x[0]=%.3f R_x[15]=%.3f | Conf=%.2f"
                        .format(leftPoints[0].x, leftPoints[15].x, rightPoints[0].x, rightPoints[15].x, orderingConfidence)
                )
            }

            val leftCurve = RailCurve(points = leftPoints, confidence = orderingConfidence)
            val rightCurve = RailCurve(points = rightPoints, confidence = orderingConfidence)

            return Pair(leftCurve, rightCurve)

        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}", e)
            return null
        }
    }

    /**
     * Converts CameraX ImageProxy to an oriented Bitmap without arbitrary cropping.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap conversion error: ${e.message}", e)
            null
        }
    }

    override fun close() {
        session.close()
        ortEnv.close()
    }
}
