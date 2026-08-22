package com.railguard.mobile.perception

import kotlin.math.abs
import kotlin.math.max

enum class AnomalyState {
    NORMAL,
    MONITORING,
    WARNING,
    ANOMALY
}

enum class AnomalyReason {
    NONE,
    LOW_RAIL_CONFIDENCE,
    INVALID_RAIL_GEOMETRY,
    GAUGE_CHANGE,
    CURVATURE_CHANGE,
    TEMPORAL_INSTABILITY
}

data class AnomalyResult(
    val state: AnomalyState,
    val reason: AnomalyReason,
    val anomalyScore: Float,
    val suspiciousFrames: Int,
    val goodFrames: Int,
    val trackStable: Boolean,
)

class AnomalyEngine(
    private val warningFrameThreshold: Int = 6, // Increased from 3
    private val anomalyFrameThreshold: Int = 15, // Increased from 8
    private val recoveryFrameThreshold: Int = 10, // Increased from 5
) {

    private var suspiciousFrames = 0
    private var goodFrames = 0

    private var previousGeometry: RailGeometryResult? = null

    fun update(
        tracking: RailTracker.TrackingResult,
    ): AnomalyResult {

        val geometry = tracking.geometry

        // Keep the previous geometry for temporal comparison.
        val oldGeometry = previousGeometry

        // Store the current geometry for the next frame.
        if (geometry.valid) {
            previousGeometry = geometry
        }

        // -------------------------------------------------------------
        // No usable rail geometry
        // -------------------------------------------------------------

        if (!geometry.valid) {

            return processSuspiciousFrame(
                score = 0.85f,
                reason = AnomalyReason.INVALID_RAIL_GEOMETRY,
                trackStable = false,
            )
        }

        // -------------------------------------------------------------
        // Calculate anomaly evidence
        // -------------------------------------------------------------

        val evidence = calculateEvidence(
            tracking = tracking,
            previousGeometry = oldGeometry,
        )

        val score = evidence.first
        val reason = evidence.second

        // -------------------------------------------------------------
        // Determine whether this frame is suspicious
        // -------------------------------------------------------------

        val suspicious = score >= 0.45f

        return if (suspicious) {

            processSuspiciousFrame(
                score = score,
                reason = reason,
                trackStable = tracking.stable
            )

        } else {

            processGoodFrame(
                score = score,
                trackStable = tracking.stable
            )
        }
    }

    // =================================================================
    // CALCULATE ANOMALY EVIDENCE
    // =================================================================

    private fun calculateEvidence(
        tracking: RailTracker.TrackingResult,
        previousGeometry: RailGeometryResult?
    ): Pair<Float, AnomalyReason> {

        val geometry = tracking.geometry

        // -------------------------------------------------------------
        // 1. Rail confidence
        // -------------------------------------------------------------

        val confidenceEvidence =
            (
                    1.0f - tracking.trackingConfidence
                    ).coerceIn(0.0f, 1.0f)

        // -------------------------------------------------------------
        // 2. Gauge variation
        // -------------------------------------------------------------

        val gaugeEvidence =
            (
                    geometry.gaugeVariation / 0.25f
                    ).coerceIn(0.0f, 1.0f)

        // -------------------------------------------------------------
        // 3. Curvature change
        // -------------------------------------------------------------

        val curvatureEvidence =
            calculateCurvatureChange(
                current = geometry,
                previous = previousGeometry
            )

        // -------------------------------------------------------------
        // 4. Temporal instability
        // -------------------------------------------------------------

        val temporalEvidence =
            (
                    1.0f - tracking.trackingConfidence
                    ).coerceIn(0.0f, 1.0f)

        // -------------------------------------------------------------
        // Weighted anomaly score
        // -------------------------------------------------------------

        val score = (
                (confidenceEvidence * 0.30f) +
                        (gaugeEvidence * 0.30f) +
                        (curvatureEvidence * 0.20f) +
                        (temporalEvidence * 0.20f)
                ).coerceIn(0.0f, 1.0f)

        // -------------------------------------------------------------
        // Primary reason
        // -------------------------------------------------------------

        val reason = when {

            confidenceEvidence > 0.70f ->
                AnomalyReason.LOW_RAIL_CONFIDENCE

            gaugeEvidence > 0.65f ->
                AnomalyReason.GAUGE_CHANGE

            curvatureEvidence > 0.65f ->
                AnomalyReason.CURVATURE_CHANGE

            temporalEvidence > 0.65f ->
                AnomalyReason.TEMPORAL_INSTABILITY

            else ->
                AnomalyReason.NONE
        }

        return Pair(
            score,
            reason
        )
    }

    // =================================================================
    // CURVATURE CHANGE
    // =================================================================

    private fun calculateCurvatureChange(
        current: RailGeometryResult,
        previous: RailGeometryResult?
    ): Float {

        if (previous == null) {
            return 0.0f
        }

        val leftDifference =
            abs(
                current.leftCurvature -
                        previous.leftCurvature
            )

        val rightDifference =
            abs(
                current.rightCurvature -
                        previous.rightCurvature
            )

        val difference =
            max(
                leftDifference,
                rightDifference
            )

        return (
                difference / 0.01f
                ).coerceIn(0.0f, 1.0f)
    }

    // =================================================================
    // SUSPICIOUS FRAME
    // =================================================================

    private fun processSuspiciousFrame(
        score: Float,
        reason: AnomalyReason,
        trackStable: Boolean
    ): AnomalyResult {

        suspiciousFrames++
        goodFrames = 0

        val state = when {

            suspiciousFrames >= anomalyFrameThreshold ->
                AnomalyState.ANOMALY

            suspiciousFrames >= warningFrameThreshold ->
                AnomalyState.WARNING

            else ->
                AnomalyState.MONITORING
        }

        return AnomalyResult(
            state = state,
            reason = reason,
            anomalyScore = score,
            suspiciousFrames = suspiciousFrames,
            goodFrames = goodFrames,
            trackStable = trackStable
        )
    }

    // =================================================================
    // GOOD FRAME
    // =================================================================

    private fun processGoodFrame(
        score: Float,
        trackStable: Boolean
    ): AnomalyResult {

        goodFrames++

        /*
         * Only clear the accumulated suspicious state
         * after several consecutive good frames.
         */
        if (goodFrames >= recoveryFrameThreshold) {
            suspiciousFrames = 0
        }

        val state = when {

            suspiciousFrames >= anomalyFrameThreshold ->
                AnomalyState.ANOMALY

            suspiciousFrames >= warningFrameThreshold ->
                AnomalyState.WARNING

            suspiciousFrames > 0 ->
                AnomalyState.MONITORING

            else ->
                AnomalyState.NORMAL
        }

        return AnomalyResult(
            state = state,
            reason =
                if (state == AnomalyState.NORMAL) {
                    AnomalyReason.NONE
                } else {
                    AnomalyReason.TEMPORAL_INSTABILITY
                },
            anomalyScore = score,
            suspiciousFrames = suspiciousFrames,
            goodFrames = goodFrames,
            trackStable = trackStable
        )
    }

}
