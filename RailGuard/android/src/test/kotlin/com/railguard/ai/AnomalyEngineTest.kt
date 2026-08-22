package com.railguard.mobile.perception

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnomalyEngineTest {

    private fun createRailCurve(
        offset: Float = 0f,
        curvature: Float = 0f,
        confidence: Float = 0.95f
    ): RailCurve {

        val rows = listOf(
            0.15f,
            0.25f,
            0.35f,
            0.45f,
            0.55f,
            0.65f,
            0.75f,
            0.90f
        )

        val points = rows.map { y ->

            val x =
                0.5f +
                        offset +
                        curvature * (y - 0.5f) * (y - 0.5f)

            RailPoint(
                x = x,
                y = y
            )
        }

        return RailCurve(
            points = points,
            confidence = confidence
        )
    }


    private fun createTrackingResult(
        leftOffset: Float = -0.15f,
        rightOffset: Float = 0.15f,
        curvature: Float = 0f,
        confidence: Float = 0.95f
    ): RailTracker.TrackingResult {

        val left =
            createRailCurve(
                offset = leftOffset,
                curvature = curvature,
                confidence = confidence
            )

        val right =
            createRailCurve(
                offset = rightOffset,
                curvature = curvature,
                confidence = confidence
            )

        val geometry =
            RailGeometry.calculate(
                left,
                right
            )

        return RailTracker.TrackingResult(
            left = left,
            right = right,
            geometry = geometry,
            trackingConfidence = confidence,
            stable = confidence >= 0.55f
        )
    }


    @Test
    fun normalTrackShouldRemainNormal() {

        val engine =
            AnomalyEngine()

        repeat(10) {

            val tracking =
                createTrackingResult()

            val result =
                engine.update(tracking)

            println(
                "NORMAL Frame $it: " +
                        "state=${result.state}, " +
                        "score=${result.anomalyScore}"
            )

            assertEquals(
                AnomalyState.NORMAL,
                result.state
            )
        }
    }


    @Test
    fun persistentLowConfidenceShouldBecomeAnomaly() {

        val engine =
            AnomalyEngine()

        var finalResult:
                AnomalyResult? = null

        repeat(10) { frame ->

            val tracking =
                createTrackingResult(
                    confidence = 0.10f
                )

            finalResult =
                engine.update(tracking)

            println(
                "LOW CONFIDENCE Frame $frame: " +
                        "state=${finalResult!!.state}, " +
                        "score=${finalResult!!.anomalyScore}, " +
                        "suspicious=${finalResult!!.suspiciousFrames}"
            )
        }

        assertTrue(
            finalResult!!.state ==
                    AnomalyState.ANOMALY
        )
    }


    @Test
    fun temporaryBadFrameShouldNotImmediatelyBecomeAnomaly() {

        val engine =
            AnomalyEngine()

        // Normal frames
        repeat(5) {

            val result =
                engine.update(
                    createTrackingResult()
                )

            assertEquals(
                AnomalyState.NORMAL,
                result.state
            )
        }

        // One bad frame
        val badResult =
            engine.update(
                createTrackingResult(
                    confidence = 0.10f
                )
            )

        println(
            "BAD FRAME: " +
                    "state=${badResult.state}, " +
                    "score=${badResult.anomalyScore}"
        )

        assertTrue(
            badResult.state !=
                    AnomalyState.ANOMALY
        )
    }


    @Test
    fun recoveryShouldClearAnomaly() {

        val engine =
            AnomalyEngine()

        // Force anomaly
        repeat(10) {

            engine.update(
                createTrackingResult(
                    confidence = 0.10f
                )
            )
        }

        // Recover
        var result:
                AnomalyResult? = null

        repeat(6) {

            result =
                engine.update(
                    createTrackingResult()
                )

            println(
                "RECOVERY Frame $it: " +
                        "state=${result!!.state}, " +
                        "score=${result!!.anomalyScore}"
            )
        }

        assertEquals(
            AnomalyState.NORMAL,
            result!!.state
        )
    }
}