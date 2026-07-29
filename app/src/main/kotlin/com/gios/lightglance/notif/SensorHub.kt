package com.gios.lightglance.notif

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * Lift-to-show and pocket suppression.
 *
 * Significant motion rather than a raw accelerometer stream: it runs on the sensor
 * hub, so the AP stays asleep until it fires. A raw accelerometer at even 50Hz would
 * cost more than the screen we are trying to be frugal about.
 */
class SensorHub(private val ctx: Context, private val onLift: () -> Unit) {

    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val sigMotion = sm?.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
    private val proximity = sm?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val handler = Handler(Looper.getMainLooper())

    /** True when something is against the panel, i.e. the phone is face-down or pocketed. */
    @Volatile
    var covered: Boolean = false
        private set

    @Volatile
    private var wantMotion = false

    private val proxListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // Most proximity sensors are binary and report either 0 or maximumRange.
            val range = e.sensor.maximumRange
            val near = if (range > 0f) range * 0.5f else 5f
            covered = e.values.firstOrNull()?.let { it < near } ?: false
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val motionTrigger = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            // One-shot by contract; the framework has already unregistered it.
            onLift()
            // Re-arming immediately would fire again on the same continuous movement.
            if (wantMotion) handler.postDelayed({ if (wantMotion) armMotion() }, 3_000)
        }
    }

    fun start() {
        proximity?.let { sm?.registerListener(proxListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        // The listener can bind while the phone is already asleep - after a process
        // restart, say - and then no SCREEN_OFF edge ever arrives to arm us.
        if (ctx.getSystemService(PowerManager::class.java)?.isInteractive != true) armMotion()
    }

    fun stop() {
        sm?.unregisterListener(proxListener)
        disarmMotion()
        handler.removeCallbacksAndMessages(null)
    }

    fun armMotion() {
        wantMotion = true
        sigMotion?.let { sm?.requestTriggerSensor(motionTrigger, it) }
    }

    fun disarmMotion() {
        wantMotion = false
        sigMotion?.let { sm?.cancelTriggerSensor(motionTrigger, it) }
    }
}
