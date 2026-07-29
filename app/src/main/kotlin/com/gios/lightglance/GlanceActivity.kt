package com.gios.lightglance

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.gios.lightglance.notif.NotifStore
import com.gios.lightglance.ui.DotField
import com.gios.lightglance.ui.theme.LightGlanceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The ambient surface.
 *
 * This is NOT a hardware always-on display. A real AOD puts the panel into a low-power
 * self-refresh mode with the application processor asleep, and that path belongs to
 * SystemUI and the panel driver - no sideloaded app can reach it. What happens here is
 * that the display is genuinely on, showing an almost entirely black frame at the
 * lowest brightness the window manager will accept. The lit pixels are nearly free on
 * OLED; the awake SoC is not.
 */
class GlanceActivity : ComponentActivity() {

    private var sensorManager: SensorManager? = null
    private var proximity: Sensor? = null

    /** Whether the keyguard was already up when we lit the panel. If it was not, the
     *  user was actively using the phone and locking it afterwards would make them
     *  re-enter their PIN to carry on with what they were doing. */
    private var keyguardWasShowing = true

    /** Pocketed mid-glance. Cheap insurance against lighting up inside a jacket. */
    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val range = event.sensor.maximumRange
            val near = if (range > 0f) range * 0.5f else 5f
            // expire(), not finish(): a bare finish drops the 2% brightness override
            // with the window and leaves the panel at full system brightness on the
            // lock screen until the sleep timer - strictly worse than the glance.
            if ((event.values.firstOrNull() ?: near) < near) expire()
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GlanceController.lastLaunchAt = SystemClock.elapsedRealtime()
        keyguardWasShowing = Screen.keyguardShowing(this)

        applyWindow()

        sensorManager = getSystemService(SensorManager::class.java)
        proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = dismiss()
        })

        val prefs = Prefs(this)

        // Lifecycle-scoped, not composition-scoped. A plain LaunchedEffect keeps
        // counting after onPause because only the frame clock pauses, so the dwell
        // could fire lockNow() seconds after the user had moved on to another app.
        if (prefs.mode != Mode.ALWAYS) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    delay(prefs.dwellSeconds * 1000L)
                    expire()
                }
            }
        }

        setContent {
            LightGlanceTheme {
                val dots by NotifStore.dots.collectAsStateWithLifecycle()
                DotField(
                    dots = dots,
                    showClock = prefs.showClock,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures { dismiss() } },
                )
            }
        }
    }

    /** singleTop means a second start can land here instead of onCreate, and none of
     *  the window state survives that. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        GlanceController.lastLaunchAt = SystemClock.elapsedRealtime()
        applyWindow()
    }

    private fun applyWindow() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Note: the LPIII has a hardware brightness wheel. If it turns out to clamp the
        // window override, this is the line that will appear to do nothing.
        window.attributes = window.attributes.apply { screenBrightness = Prefs(this@GlanceActivity).brightness }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** A deliberate user dismissal, which arms the cooldown in GlanceController. */
    private fun dismiss() {
        GlanceController.userDismissed()
        expire()
    }

    /**
     * Finishing alone only drops FLAG_KEEP_SCREEN_ON; the panel would then stay lit
     * until LightOS's sleep timer. Ask device admin to darken it now. No-op without
     * the grant, in which case dwell becomes a floor rather than a duration.
     */
    private fun expire() {
        if (isFinishing || isDestroyed) return
        finish()
        // lockNow() also raises the keyguard, bypassing the usual grace period. Only
        // worth doing if the keyguard was up before we interrupted.
        if (keyguardWasShowing) Screen.sleep(this)
    }

    override fun onResume() {
        super.onResume()
        proximity?.let {
            sensorManager?.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(proximityListener)
        if (!isFinishing) {
            // Something else took the foreground - almost always the power button in
            // ALWAYS mode. Treat it as a dismissal or the SCREEN_OFF that follows will
            // wake the phone straight back up and the user cannot turn it off.
            GlanceController.userDismissed()
            finish()
        }
    }
}
