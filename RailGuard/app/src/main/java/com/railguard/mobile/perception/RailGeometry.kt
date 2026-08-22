package com.railguard.mobile.perception

import kotlin.math.abs

/**
 * A point on a detected rail.
 */
data class RailPoint(
    val x: Float,
    val y: Float,
)


/**
 * Represents one detected rail.
 */
data class RailCurve(
    val points: List<RailPoint>,
    val confidence: Float,
)


/**
 * Geometry calculated from the two rails.
 */
data class RailGeometryResult(
    val valid: Boolean,
    val bottomGauge: Float,
    val middleGauge: Float,
    val gaugeVariation: Float,
    val vanishingPointX: Float,
    val leftCurvature: Float,
    val rightCurvature: Float,
    val confidence: Float,
)


object RailGeometry {

    fun calculate(
        left: RailCurve,
        right: RailCurve,
    ): RailGeometryResult {

        if (left.points.size < 3 || right.points.size < 3) {
            return invalidResult()
        }

        if (left.confidence < 0.1f || right.confidence < 0.1f) {
            return invalidResult()
        }

        val bottomLeft = interpolateX(left.points, 0.90f)
        val bottomRight = interpolateX(right.points, 0.90f)
        val middleLeft = interpolateX(left.points, 0.55f)
        val middleRight = interpolateX(right.points, 0.55f)

        if (bottomLeft == null || bottomRight == null || middleLeft == null || middleRight == null) {
            return invalidResult()
        }

        val bottomGauge = abs(bottomRight - bottomLeft)
        val middleGauge = abs(middleRight - middleLeft)

        val perspectiveRatio = if (bottomGauge > 0.001f) middleGauge / bottomGauge else 0.0f
        val ySpan = abs(left.points.first().y - left.points.last().y)
        val isFullTrack = ySpan > 0.30f

        val gaugeVariation = if (perspectiveRatio in 0.10f..0.98f) {
            0.0f
        } else if (perspectiveRatio > 0.98f) {
            if (isFullTrack) ((perspectiveRatio - 0.98f) / 0.20f).coerceIn(0.0f, 1.0f) else 0.0f
        } else {
            ((0.10f - perspectiveRatio) / 0.10f).coerceIn(0.0f, 1.0f)
        }

        val topLeft = interpolateX(left.points, 0.15f)
        val topRight = interpolateX(right.points, 0.15f)
        val vanishingPointX = if (topLeft != null && topRight != null) {
            (topLeft + topRight) / 2.0f
        } else {
            0.5f
        }

        val leftCurvature = calculateCurvature(left.points)
        val rightCurvature = calculateCurvature(right.points)
        val geometryConfidence = calculateGeometryConfidence(bottomGauge, middleGauge, gaugeVariation, vanishingPointX)

        val combinedConfidence = (left.confidence * right.confidence * geometryConfidence).coerceIn(0.0f, 1.0f)

        return RailGeometryResult(
            valid = true,
            bottomGauge = bottomGauge,
            middleGauge = middleGauge,
            gaugeVariation = gaugeVariation,
            vanishingPointX = vanishingPointX,
            leftCurvature = leftCurvature,
            rightCurvature = rightCurvature,
            confidence = combinedConfidence
        )
    }

    private fun interpolateX(points: List<RailPoint>, targetY: Float): Float? {
        if (points.size < 2) return null
        val sorted = points.sortedBy { it.y }
        for (i in 0 until (sorted.size - 1)) {
            val p1 = sorted[i]
            val p2 = sorted[i + 1]
            if (targetY >= p1.y && targetY <= p2.y) {
                val dy = p2.y - p1.y
                if (abs(dy) < 0.00001f) return p1.x
                val t = (targetY - p1.y) / dy
                return (p1.x + t * (p2.x - p1.x))
            }
        }
        if (targetY < sorted.first().y) return sorted.first().x
        if (targetY > sorted.last().y) return sorted.last().x
        return null
    }

    private fun calculateCurvature(points: List<RailPoint>): Float {
        if (points.size < 3) return 0.0f
        val p1 = points.first()
        val p2 = points[points.size / 2]
        val p3 = points.last()
        val y1 = p1.y; val y2 = p2.y; val y3 = p3.y
        val x1 = p1.x; val x2 = p2.x; val x3 = p3.x
        val denominator = (y1 - y2) * (y1 - y3) * (y2 - y3)
        if (abs(denominator) < 0.000001f) return 0.0f
        return (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2)) / denominator
    }

    private fun calculateGeometryConfidence(bottomGauge: Float, middleGauge: Float, gaugeVariation: Float, vanishingPointX: Float): Float {
        if (bottomGauge <= 0.01f || middleGauge <= 0.01f) return 0.0f
        val gaugeScore = if (bottomGauge > 0.03f && bottomGauge < 0.80f) 1.0f else 0.2f
        val variationScore = (1.0f - gaugeVariation).coerceIn(0.0f, 1.0f)
        val vanishingScore = if (vanishingPointX in 0.0f..1.0f) 1.0f else 0.0f
        return (gaugeScore * 0.4f + variationScore * 0.4f + vanishingScore * 0.2f).coerceIn(0.0f, 1.0f)
    }

    private fun invalidResult(): RailGeometryResult {
        return RailGeometryResult(false, 0.0f, 0.0f, 1.0f, 0.5f, 0.0f, 0.0f, 0.0f)
    }
}
