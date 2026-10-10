package com.railguard.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.railguard.mobile.perception.LidarScan
import kotlin.math.cos
import kotlin.math.sin

/**
 * A Composable that displays a real-time LiDAR map.
 * Centers the map and scales points.
 */
@Composable
fun LidarMapScreen(
    scan: LidarScan,
    modifier: Modifier = Modifier,
    maxDistanceMm: Float = 8000f,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2
            val scale = radius / maxDistanceMm

            // Draw circular grid
            val gridColor = Color.DarkGray
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            
            // 2m, 4m, 6m, 8m rings
            for (i in 1..4) {
                val ringRadius = (i * 2000f) * scale
                drawCircle(
                    color = gridColor,
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1f, pathEffect = dashEffect)
                )
            }

            // Draw crosshair
            drawLine(
                color = gridColor,
                start = Offset(0f, center.y),
                end = Offset(size.width, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = gridColor,
                start = Offset(center.x, 0f),
                end = Offset(center.x, size.height),
                strokeWidth = 1f
            )

            // Draw points
            scan.points.forEach { point ->
                // Convert polar to Cartesian
                // YDLIDAR 0 degrees is typically at the back or front, 
                // depending on mounting. Let's assume 0 is "Up" (North).
                val angleRad = Math.toRadians((point.angle - 90.0)).toFloat()
                val x = center.x + (point.distance * scale * cos(angleRad)).toFloat()
                val y = center.y + (point.distance * scale * sin(angleRad)).toFloat()

                drawCircle(
                    color = Color.Cyan,
                    radius = 3f,
                    center = Offset(x, y)
                )
            }

            // Draw "Robot" center
            drawCircle(
                color = Color.Red,
                radius = 6f,
                center = center
            )
        }
    }
}
