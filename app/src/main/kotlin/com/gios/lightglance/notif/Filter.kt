package com.gios.lightglance.notif

import android.app.Notification
import android.app.NotificationManager
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification

/**
 * On LightOS everything worth a dot lands at importance >= 3 and everything that isn't
 * (media transports, VPN and player foreground services, the framework's own
 * ranker_group placeholders) lands at 2, so importance does nearly all the work here.
 * The flag checks only catch the group summaries, which are importance 4 but duplicate
 * the children underneath them.
 */
fun isDotWorthy(
    sbn: StatusBarNotification,
    selfPkg: String,
    ranking: RankingMap?,
    scratch: Ranking,
): Boolean {
    val n = sbn.notification ?: return false
    if (sbn.packageName == selfPkg) return false
    if (sbn.packageName == "android") return false
    if (sbn.tag == "ranker_group") return false
    if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
    if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return false
    if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return false
    if (n.category == Notification.CATEGORY_TRANSPORT) return false
    if (n.category == Notification.CATEGORY_SERVICE) return false

    // No ranking entry should not silently mean "drop it" - fail open, the flag and
    // package checks above have already removed the genuinely noisy cases.
    val ranked = ranking?.getRanking(sbn.key, scratch) ?: false
    if (!ranked) return true
    return scratch.importance >= NotificationManager.IMPORTANCE_DEFAULT
}
