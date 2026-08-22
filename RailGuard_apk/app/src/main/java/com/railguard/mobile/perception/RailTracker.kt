package com.railguard.mobile.perception

import kotlin.math.abs

/**
 * Temporal rail tracker.
 */
class RailTracker(
    private val smoothingFactor: Float = 0.35f, 
) {
    private var previousLeft: RailCurve? = null
    private var previousRight: RailCurve? = null
    private var consecutiveGoodFrames = 0
    private var consecutiveBadFrames = 0

    data class TrackingResult(
        val left: RailCurve?,
        val right: RailCurve?,
        val geometry: RailGeometryResult,
        val trackingConfidence: Float,
        val stable: Boolean,
    )

    fun update(
        detectedLeft: RailCurve?,
        detectedRight: RailCurve?,
    ): TrackingResult {

        if (detectedLeft == null || detectedRight == null) {
            consecutiveBadFrames++
            consecutiveGoodFrames = 0
            return TrackingResult(
                left = previousLeft,
                right = previousRight,
                geometry = if (previousLeft != null && previousRight != null) {
                    RailGeometry.calculate(previousLeft!!, previousRight!!)
                } else {
                    invalidGeometry()
                },
                trackingConfidence = 0.0f,
                stable = false
            )
        }

        val smoothedLeft = if (previousLeft != null) {
            smoothCurve(previousLeft!!, detectedLeft)
        } else {
            detectedLeft
        }

        val smoothedRight = if (previousRight != null) {
            smoothCurve(previousRight!!, detectedRight)
        } else {
            detectedRight
        }

        val orderingValid = checkRailOrdering(smoothedLeft, smoothedRight)

        if (!orderingValid) {
            consecutiveBadFrames++
            consecutiveGoodFrames = 0
            return TrackingResult(
                left = previousLeft,
                right = previousRight,
                geometry = if (previousLeft != null && previousRight != null) {
                    RailGeometry.calculate(previousLeft!!, previousRight!!)
                } else {
                    invalidGeometry()
                },
                trackingConfidence = 0.1f,
                stable = false
            )
        }

        val geometry = RailGeometry.calculate(smoothedLeft, smoothedRight)
        val temporalConfidence = if (previousLeft != null && previousRight != null) {
            calculateTemporalConsistency(previousLeft!!, previousRight!!, smoothedLeft, smoothedRight)
        } else {
            1.0f
        }

        val trackingConfidence = (geometry.confidence * temporalConfidence).coerceIn(0.0f, 1.0f)

        if (trackingConfidence >= 0.30f) {
            consecutiveGoodFrames++
            consecutiveBadFrames = 0
        } else {
            consecutiveBadFrames++
            consecutiveGoodFrames = 0
        }

        previousLeft = smoothedLeft
        previousRight = smoothedRight

        return TrackingResult(
            left = smoothedLeft,
            right = smoothedRight,
            geometry = geometry,
            trackingConfidence = trackingConfidence,
            stable = consecutiveGoodFrames >= 1
        )
    }

    private fun smoothCurve(previous: RailCurve, current: RailCurve): RailCurve {
        if (previous.points.size != current.points.size) {
            return current
        }
        val points = previous.points.indices.map {
            val oldPoint = previous.points[it]
            val newPoint = current.points[it]
            RailPoint(
                x = oldPoint.x + (smoothingFactor * (newPoint.x - oldPoint.x)),
                y = oldPoint.y + (smoothingFactor * (newPoint.y - oldPoint.y))
            )
        }
        val confidence = previous.confidence + (smoothingFactor * (current.confidence - previous.confidence))
        return RailCurve(points = points, confidence = confidence)
    }

    private fun checkRailOrdering(left: RailCurve, right: RailCurve): Boolean {
        if (left.points.isEmpty() || right.points.isEmpty()) {
            return false
        }
        val sampleCount = minOf(left.points.size, right.points.size)
        var validSamples = 0
        for (i in 0 until sampleCount) {
            if (left.points[i].x < right.points[i].x) {
                validSamples++
            }
        }
        return (validSamples.toFloat() / sampleCount) >= 0.6f
    }

    private fun calculateTemporalConsistency(
        previousLeft: RailCurve, previousRight: RailCurve,
        currentLeft: RailCurve, currentRight: RailCurve
    ): Float {
        if (previousLeft.points.size != currentLeft.points.size || previousRight.points.size != currentRight.points.size) {
            return 0.5f
        }
        var totalDifference = 0.0f
        var count = 0
        for (i in currentLeft.points.indices) {
            totalDifference += abs(currentLeft.points[i].x - previousLeft.points[i].x)
            totalDifference += abs(currentRight.points[i].x - previousRight.points[i].x)
            count += 2
        }
        val averageDifference = if (count > 0) totalDifference / count else 1.0f
        return (1.0f - averageDifference * 10.0f).coerceIn(0.0f, 1.0f)
    }

    fun reset() {
        previousLeft = null
        previousRight = null
        consecutiveGoodFrames = 0
        consecutiveBadFrames = 0
    }

    private fun invalidGeometry(): RailGeometryResult {
        return RailGeometryResult(
            valid = false,
            bottomGauge = 0.0f,
            middleGauge = 0.0f,
            gaugeVariation = 1.0f,
            vanishingPointX = 0.5f,
            leftCurvature = 0.0f,
            rightCurvature = 0.0f,
            confidence = 0.0f
        )
    }
}
