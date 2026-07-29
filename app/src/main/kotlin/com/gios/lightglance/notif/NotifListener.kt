package com.gios.lightglance.notif

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import com.gios.lightglance.GlanceController
import com.gios.lightglance.Prefs
import com.gios.lightglance.Trigger

/**
 * The app's only long-lived component. The system binds notification listeners and keeps
 * them running, so this replaces the foreground service an app like this would normally
 * need - and avoids adding a permanent notification of our own to the list we read.
 */
class NotifListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListener"
        private const val RANKING_DEBOUNCE_MS = 250L

        @Volatile var connected: Boolean = false
            private set
    }

    private var hub: SensorHub? = null
    private var receiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { Prefs(this) }

    /** notification key -> postTime, so an app that edits one notification in place
     *  still wakes the panel for the second and third message. */
    private var seen: Map<String, Long> = emptyMap()

    private val rankingRefresh = Runnable { refresh(wake = false) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    hub?.armMotion()
                    GlanceController.show(this@NotifListener, Trigger.SCREEN_OFF, hub?.covered == true)
                }
                Intent.ACTION_SCREEN_ON -> hub?.disarmMotion()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        Log.i(TAG, "listener connected")
        refresh(wake = false)

        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }

        // Reconnects without an intervening disconnect are possible; a second hub would
        // leave the first one's proximity listener registered for the life of the process.
        hub?.stop()
        hub = SensorHub(this) {
            GlanceController.show(this, Trigger.LIFT, hub?.covered == true)
        }.also { it.start() }
    }

    override fun onListenerDisconnected() {
        connected = false
        handler.removeCallbacks(rankingRefresh)
        hub?.stop()
        hub = null
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        // Deliberately not clearing NotifStore: a rebind underneath a live ALWAYS-mode
        // surface would blank it, and onListenerConnected rebuilds the set anyway.
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        refresh(wake = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        refresh(wake = false)
    }

    /** Fires on DND changes, importance edits, app-op changes - far too often to do a
     *  full binder round-trip on each one. */
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        handler.removeCallbacks(rankingRefresh)
        handler.postDelayed(rankingRefresh, RANKING_DEBOUNCE_MS)
    }

    private fun refresh(wake: Boolean) {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = runCatching { currentRanking }.getOrNull()
        val scratch = Ranking()

        val counts = LinkedHashMap<String, Int>()
        val current = HashMap<String, Long>(active.size)
        var somethingNew = false

        for (sbn in active) {
            if (!isDotWorthy(sbn, packageName, ranking, scratch)) continue
            counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
            current[sbn.key] = sbn.postTime

            val previous = seen[sbn.key]
            val alertOnce = sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
            if (previous == null || (sbn.postTime > previous && !alertOnce)) somethingNew = true
        }

        seen = current
        NotifStore.publish(counts.map { (pkg, n) -> SlotMap.dotFor(prefs, pkg, n) })

        if (wake && somethingNew) {
            GlanceController.show(this, Trigger.POST, hub?.covered == true)
        }
    }
}
