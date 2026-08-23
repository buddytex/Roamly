package com.railguard.mobile.perception

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Orientation(
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val yaw: Float = 0f
)

class ImuManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _orientation = MutableStateFlow(Orientation())
    val orientation: StateFlow<Orientation> = _orientation.asStateFlow()

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            
            val orientationValues = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationValues)
            
            // Values are in radians: 0: yaw, 1: pitch, 2: roll
            _orientation.value = Orientation(
                yaw = Math.toDegrees(orientationValues[0].toDouble()).toFloat(),
                pitch = Math.toDegrees(orientationValues[1].toDouble()).toFloat(),
                roll = Math.toDegrees(orientationValues[2].toDouble()).toFloat()
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for now
    }
}
