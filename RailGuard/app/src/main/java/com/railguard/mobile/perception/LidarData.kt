package com.railguard.mobile.perception

/**
 * Represents a single point in space from the LiDAR sensor.
 * @param angle The angle in degrees (0 to 360).
 * @param distance The distance in millimeters.
 */
data class LidarPoint(
    val angle: Float,
    val distance: Float
)

/**
 * Represents a full 360-degree scan from the LiDAR sensor.
 * @param points The list of [LidarPoint]s in the scan.
 * @param timestamp The time the scan was completed.
 */
data class LidarScan(
    val points: List<LidarPoint> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
