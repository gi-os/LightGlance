package com.gios.lightglance

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.gios.lightglance.admin.GlanceAdminReceiver

object Screen {

    /** `lockNow()` broadcasts ACTION_SCREEN_OFF exactly like a power press does
     *  (DevicePolicyManagerService -> goToSleep with GO_TO_SLEEP_REASON_DEVICE_ADMIN).
     *  Without this stamp, ALWAYS mode treats its own sleep as a user press and wakes
     *  straight back up. */
    @Volatile
    private var selfSleptAt = 0L

    fun sleptOurselvesWithin(ms: Long): Boolean =
        SystemClock.elapsedRealtime() - selfSleptAt < ms

    fun adminComponent(ctx: Context) = ComponentName(ctx, GlanceAdminReceiver::class.java)

    fun canSleep(ctx: Context): Boolean =
        ctx.getSystemService(DevicePolicyManager::class.java)
            ?.isAdminActive(adminComponent(ctx)) == true

    fun keyguardShowing(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /**
     * Put the panel back to sleep immediately. Degrades to a no-op if device admin was
     * never granted, in which case the screen simply times out normally and the dwell
     * setting becomes a lower bound rather than a duration.
     */
    fun sleep(ctx: Context) {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java) ?: return
        if (!dpm.isAdminActive(adminComponent(ctx))) return
        selfSleptAt = SystemClock.elapsedRealtime()
        runCatching { dpm.lockNow() }.onFailure { Log.w("Screen", "lockNow failed", it) }
    }

    fun adminCommand(ctx: Context) =
        "adb shell dpm set-active-admin --user 0 " +
            "${ctx.packageName}/${GlanceAdminReceiver::class.java.name}"
}
