package com.gios.lightglance.admin

import android.app.admin.DeviceAdminReceiver

/**
 * Exists purely for `force-lock`. Nothing here manages a device.
 *
 * Without it, finishing the ambient surface only releases FLAG_KEEP_SCREEN_ON and the
 * panel then stays lit until LightOS's own sleep timer runs out - so an eight second
 * dwell would really be eight seconds plus up to ten minutes. `lockNow()` is the only
 * way an unprivileged app can put the screen back to sleep on demand.
 */
class GlanceAdminReceiver : DeviceAdminReceiver()
