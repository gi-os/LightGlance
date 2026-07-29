package com.gios.lightglance

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.gios.lightglance.notif.Dot
import com.gios.lightglance.notif.NotifStore

enum class Trigger { POST, LIFT, SCREEN_OFF, TEST }

object GlanceController {

    private const val TAG = "GlanceController"

    private const val RATE_LIMIT_MS = 2_000L

    /** Walking fires significant motion every few seconds. Without a cooldown of its
     *  own, one pending notification plus a walk to the subway holds the panel on for
     *  eight seconds out of every ten. */
    private const val LIFT_COOLDOWN_MS = 60_000L
    private const val LIFT_SAME_CONTENT_MS = 300_000L

    /** A SCREEN_OFF arriving this soon after our own lockNow() is our own doing. */
    private const val SELF_SLEEP_WINDOW_MS = 1_500L

    private const val DISMISS_COOLDOWN_MS = 90_000L

    @Volatile private var suppressUntil = 0L
    @Volatile private var lastShown = 0L
    @Volatile private var lastLiftShown = 0L
    @Volatile private var lastShownSet: List<Dot> = emptyList()

    /** Stamped by GlanceActivity.onCreate, so we can tell a swallowed start from a real one. */
    @Volatile var lastLaunchAt = 0L

    @Volatile var lastSkipReason: String = "-"
        private set

    private val handler = Handler(Looper.getMainLooper())

    fun userDismissed() {
        suppressUntil = SystemClock.elapsedRealtime() + DISMISS_COOLDOWN_MS
    }

    fun show(ctx: Context, trigger: Trigger, covered: Boolean = false) {
        val prefs = Prefs(ctx)
        val now = SystemClock.elapsedRealtime()
        val dots = NotifStore.dots.value

        fun skip(why: String) {
            lastSkipReason = "$trigger: $why"
            Log.d(TAG, lastSkipReason)
        }

        if (trigger != Trigger.TEST) {
            if (prefs.mode == Mode.OFF) return skip("mode off")
            if (covered) return skip("proximity covered")
            if (now - lastShown < RATE_LIMIT_MS) return skip("rate limited")

            // The dismissal cooldown exists to stop wake loops, not to mute the phone.
            // A genuinely new notification always gets through it.
            if (trigger != Trigger.POST && now < suppressUntil) {
                return skip("suppressed after dismissal")
            }

            when (trigger) {
                Trigger.LIFT -> {
                    if (!prefs.liftToShow) return skip("lift disabled")
                    if (now - lastLiftShown < LIFT_COOLDOWN_MS) return skip("lift cooldown")
                    if (dots == lastShownSet && now - lastLiftShown < LIFT_SAME_CONTENT_MS) {
                        return skip("nothing new since last lift")
                    }
                }
                Trigger.SCREEN_OFF -> {
                    if (prefs.mode != Mode.ALWAYS) return skip("not always mode")
                    if (Screen.sleptOurselvesWithin(SELF_SLEEP_WINDOW_MS)) {
                        return skip("our own lockNow")
                    }
                }
                else -> Unit
            }

            if (dots.isEmpty() && prefs.mode != Mode.ALWAYS) return skip("nothing to show")

            val pm = ctx.getSystemService(PowerManager::class.java)
            if (pm?.isInteractive == true && trigger != Trigger.SCREEN_OFF) {
                return skip("screen already on")
            }

            // A background-activity-start block does NOT throw. ActivityStarter returns
            // START_ABORTED and Instrumentation only raises for codes in [-100,-1], so
            // without this pre-check the setup screen would cheerfully report "shown"
            // in precisely the case it exists to diagnose.
            if (!Grants.canStartFromBackground(ctx)) {
                return skip("no background-start appop")
            }
        }

        lastShown = now
        if (trigger == Trigger.LIFT) lastLiftShown = now
        lastShownSet = dots
        lastSkipReason = "$trigger: starting"

        val stamp = lastLaunchAt
        ctx.startActivity(
            Intent(ctx, GlanceActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            ),
        )

        // Belt and braces for the silent-abort case above.
        handler.postDelayed({
            if (lastLaunchAt == stamp) {
                lastSkipReason = "$trigger: start swallowed"
                Log.w(TAG, lastSkipReason)
            } else {
                lastSkipReason = "$trigger: shown"
            }
        }, 800)
    }
}
