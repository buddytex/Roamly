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
import androidx.compose.ui.unit.dp
import com.railguard.mobile.perception.Orientation
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ImuVisualization(
    orientation: Orientation,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF1E1E1E), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val axisLength = size.minDimension / 2.5f

            // Convert degrees to radians
            val pitchRad = Math.toRadians(orientation.pitch.toDouble()).toFloat()
            val rollRad = Math.toRadians(orientation.roll.toDouble()).toFloat()
            val yawRad = Math.toRadians(orientation.yaw.toDouble()).toFloat()

            fun project(x: Float, y: Float, z: Float): Offset {
                // Rotate around X (pitch)
                val y1 = y * cos(pitchRad) - z * sin(pitchRad)
                val z1 = y * sin(pitchRad) + z * cos(pitchRad)
                
                // Rotate around Y (roll)
                val x1 = x * cos(rollRad) + z1 * sin(rollRad)
                // val z2 = -x * sin(rollRad) + z1 * cos(rollRad)
                
                // Rotate around Z (yaw)
                val x2 = x1 * cos(yawRad) - y1 * sin(yawRad)
                val y2 = x1 * sin(yawRad) + y1 * cos(yawRad)
                
                return Offset(center.x + x2 * axisLength, center.y - y2 * axisLength)
            }

            // Draw X-axis (Red)
            drawLine(Color.Red, center, project(1f, 0f, 0f), strokeWidth = 4f)
            
            // Draw Y-axis (Green)
            drawLine(Color.Green, center, project(0f, 1f, 0f), strokeWidth = 4f)
            
            // Draw Z-axis (Blue)
            drawLine(Color.Blue, center, project(0f, 0f, 1f), strokeWidth = 4f)
            
            drawCircle(Color.White, radius = 4f, center = center)
        }
    }
}
