package com.railguard.mobile.perception

import kotlin.math.abs
import kotlin.math.pow

/**
 * A point on a detected rail.
 *
 * Coordinates are normalized:
 *
 * x = 0.0 -> left side of image
 * x = 1.0 -> right side of image
 * y = 0.0 -> top of image
 * y = 1.0 -> bottom of image
 */
data class RailPoint(
    val x: Float,
    val y: Float
)


/**
 * Represents one detected rail.
 */
data class RailCurve(
    val points: List<RailPoint>,
    val confidence: Float
)


/**
 * Geometry calculated from the two rails.
 */
data class RailGeometryResult(

    val valid: Boolean,

    // Distance between rails at the bottom of the image.
    val bottomGauge: Float,

    // Distance between rails near the middle of the image.
    val middleGauge: Float,

    // Difference between bottom and middle gauge.
    val gaugeVariation: Float,

    // Approximate vanishing point.
    val vanishingPointX: Float,

    // Approximate curvature of left rail.
    val leftCurvature: Float,

    // Approximate curvature of right rail.
    val rightCurvature: Float,

    // Combined confidence.
    val confidence: Float
)


object RailGeometry {


    /**
     * Calculate geometry from left and right rail curves.
     */
    fun calculate(

        left: RailCurve,

        right: RailCurve

    ): RailGeometryResult {


        // -------------------------------------------------------------
        // Basic validation
        // -------------------------------------------------------------

        if (left.points.size < 3 ||
            right.points.size < 3
        ) {

            return invalidResult()
        }


        if (left.confidence < 0.1f ||
            right.confidence < 0.1f
        ) {

            return invalidResult()
        }


        // -------------------------------------------------------------
        // Gauge
        // -------------------------------------------------------------

        val bottomLeft =
            interpolateX(
                left.points,
                0.90f
            )

        val bottomRight =
            interpolateX(
                right.points,
                0.90f
            )


        val middleLeft =
            interpolateX(
                left.points,
                0.55f
            )

        val middleRight =
            interpolateX(
                right.points,
                0.55f
            )


        if (
            bottomLeft == null ||
            bottomRight == null ||
            middleLeft == null ||
            middleRight == null
        ) {

            return invalidResult()
        }


        val bottomGauge =
            abs(
                bottomRight - bottomLeft
            )


        val middleGauge =
            abs(
                middleRight - middleLeft
            )


        val gaugeVariation =

            if (middleGauge > 0.001f) {

                abs(
                    bottomGauge -
                            middleGauge
                ) / middleGauge

            } else {

                1.0f
            }


        // -------------------------------------------------------------
        // Vanishing point
        // -------------------------------------------------------------

        val topLeft =
            interpolateX(
                left.points,
                0.15f
            )

        val topRight =
            interpolateX(
                right.points,
                0.15f
            )


        val vanishingPointX =

            if (
                topLeft != null &&
                topRight != null
            ) {

                (topLeft + topRight) / 2.0f

            } else {

                0.5f
            }


        // -------------------------------------------------------------
        // Curvature
        // -------------------------------------------------------------

        val leftCurvature =
            calculateCurvature(
                left.points
            )

        val rightCurvature =
            calculateCurvature(
                right.points
            )


        // -------------------------------------------------------------
        // Confidence
        // -------------------------------------------------------------

        val geometryConfidence =
            calculateGeometryConfidence(
                bottomGauge,
                middleGauge,
                gaugeVariation,
                vanishingPointX
            )


        val combinedConfidence =

            (
                    left.confidence *
                            right.confidence *
                            geometryConfidence
                    ).coerceIn(
                    0.0f,
                    1.0f
                )


        return RailGeometryResult(

            valid = true,

            bottomGauge =
                bottomGauge,

            middleGauge =
                middleGauge,

            gaugeVariation =
                gaugeVariation,

            vanishingPointX =
                vanishingPointX,

            leftCurvature =
                leftCurvature,

            rightCurvature =
                rightCurvature,

            confidence =
                combinedConfidence
        )
    }


    // =================================================================
    // INTERPOLATE X AT A GIVEN Y
    // =================================================================

    private fun interpolateX(

        points: List<RailPoint>,

        targetY: Float

    ): Float? {

        if (points.size < 2) {
            return null
        }


        val sorted =
            points.sortedBy {
                it.y
            }


        // -------------------------------------------------------------
        // Find two points surrounding targetY.
        // -------------------------------------------------------------

        for (i in 0 until sorted.size - 1) {

            val p1 = sorted[i]
            val p2 = sorted[i + 1]


            if (
                targetY >= p1.y &&
                targetY <= p2.y
            ) {

                val dy =
                    p2.y - p1.y


                if (abs(dy) < 0.00001f) {

                    return p1.x
                }


                val t =
                    (targetY - p1.y) / dy


                return (
                        p1.x +
                                t *
                                (p2.x - p1.x)
                        )
            }
        }


        // -------------------------------------------------------------
        // If target is outside the visible range,
        // use the nearest endpoint.
        // -------------------------------------------------------------

        if (
            targetY <
            sorted.first().y
        ) {

            return sorted.first().x
        }


        if (
            targetY >
            sorted.last().y
        ) {

            return sorted.last().x
        }


        return null
    }


    // =================================================================
    // CURVATURE
    // =================================================================

    private fun calculateCurvature(

        points: List<RailPoint>

    ): Float {

        if (points.size < 3) {
            return 0.0f
        }


        /*
         * We approximate the rail with:
         *
         * x = a*y² + b*y + c
         *
         * The quadratic coefficient 'a' gives us
         * a simple curvature indicator.
         */


        val p1 =
            points.first()

        val p2 =
            points[points.size / 2]

        val p3 =
            points.last()


        val y1 = p1.y
        val y2 = p2.y
        val y3 = p3.y


        val x1 = p1.x
        val x2 = p2.x
        val x3 = p3.x


        val denominator =

            (y1 - y2) *
                    (y1 - y3) *
                    (y2 - y3)


        if (
            abs(denominator) <
            0.000001f
        ) {

            return 0.0f
        }


        val a =

            (
                    x1 * (y2 - y3) +
                            x2 * (y3 - y1) +
                            x3 * (y1 - y2)
                    ) / denominator


        return a
    }


    // =================================================================
    // GEOMETRY CONFIDENCE
    // =================================================================

    private fun calculateGeometryConfidence(

        bottomGauge: Float,

        middleGauge: Float,

        gaugeVariation: Float,

        vanishingPointX: Float

    ): Float {


        if (
            bottomGauge <= 0.01f ||
            middleGauge <= 0.01f
        ) {

            return 0.0f
        }


        /*
         * We don't know the physical railway gauge
         * from the camera alone yet.
         *
         * Therefore this is NOT a "track is safe"
         * score.
         *
         * It only measures whether the detected
         * geometry is internally plausible.
         */


        val gaugeScore =

            if (
                bottomGauge > 0.03f &&
                bottomGauge < 0.80f
            ) {

                1.0f

            } else {

                0.2f
            }


        val variationScore =

            (1.0f -
                    gaugeVariation)
                .coerceIn(
                    0.0f,
                    1.0f
                )


        val vanishingScore =

            if (
                vanishingPointX >= 0.0f &&
                vanishingPointX <= 1.0f
            ) {

                1.0f

            } else {

                0.0f
            }


        return (

                gaugeScore * 0.4f +
                        variationScore * 0.4f +
                        vanishingScore * 0.2f

                ).coerceIn(
                0.0f,
                1.0f
            )
    }


    // =================================================================
    // INVALID RESULT
    // =================================================================

    private fun invalidResult():

            RailGeometryResult {

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