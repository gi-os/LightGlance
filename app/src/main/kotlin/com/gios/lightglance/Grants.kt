package com.gios.lightglance

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.gios.lightglance.notif.NotifListener

/**
 * Neither grant has a Settings screen on LightOS, so both are adb-only in practice.
 * That is also the reason this app can never be distributed to anyone who will not
 * plug their phone into a computer once.
 */
object Grants {

    fun listenerEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            ?: return false
        val self = ComponentName(ctx, NotifListener::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == self }
    }

    fun canStartFromBackground(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun listenerCommand(ctx: Context) =
        "adb shell cmd notification allow_listener ${ctx.packageName}/${NotifListener::class.java.name}"

    fun overlayCommand(ctx: Context) =
        "adb shell appops set ${ctx.packageName} SYSTEM_ALERT_WINDOW allow"
}
