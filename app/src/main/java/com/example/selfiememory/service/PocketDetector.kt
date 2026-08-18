package com.example.selfiememory.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PocketDetector @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    suspend fun isLikelyInPocket(): Boolean {
        val proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return false
        val light = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        var isNear: Boolean? = null
        var lux: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_PROXIMITY -> isNear = event.values[0] < event.sensor.maximumRange
                    Sensor.TYPE_LIGHT -> lux = event.values[0]
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        return try {
            sensorManager.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
            light?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
            delay(400)
            isNear == true && (light == null || (lux != null && lux!! < 4f))
        } finally {
            sensorManager.unregisterListener(listener)
        }
    }
}
