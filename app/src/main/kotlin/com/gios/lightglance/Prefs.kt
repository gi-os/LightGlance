package com.gios.lightglance

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

enum class Mode {
    /** Listener stays bound, nothing ever lights up. */
    OFF,

    /** Wake briefly when something arrives, and on lift. The sane default. */
    POKE,

    /** Hold the panel on indefinitely whenever the phone would otherwise sleep.
     *  This is not a hardware always-on display and costs real battery. */
    ALWAYS,
}

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("glance", Context.MODE_PRIVATE)

    var mode: Mode
        get() = runCatching { Mode.valueOf(sp.getString("mode", null) ?: "POKE") }.getOrDefault(Mode.POKE)
        set(v) = sp.edit().putString("mode", v.name).apply()

    var liftToShow: Boolean
        get() = sp.getBoolean("lift", true)
        set(v) = sp.edit().putBoolean("lift", v).apply()

    var showClock: Boolean
        get() = sp.getBoolean("clock", true)
        set(v) = sp.edit().putBoolean("clock", v).apply()

    /** How long the panel stays lit in POKE mode. */
    var dwellSeconds: Int
        get() = sp.getInt("dwell", 8)
        set(v) = sp.edit().putInt("dwell", v).apply()

    /** Window brightness override, 0f..1f. The floor is deliberately not 0f: some
     *  panels read 0f as "use the system value" rather than "as dim as possible". */
    var brightness: Float
        get() = sp.getFloat("brightness", 0.02f)
        set(v) = sp.edit().putFloat("brightness", v.coerceIn(0.01f, 1f)).apply()

    /**
     * Slots are sticky. A package that shows up once keeps its x-position forever,
     * otherwise the dot you learned to read as "chat" silently becomes something else
     * the first time an unrelated app posts.
     *
     * Cached in memory because this is called once per active notification on every
     * listener callback, and `SharedPreferences.all` copies the entire map each time.
     */
    fun slotFor(pkg: String, maxSlots: Int): Int {
        loadSlots()
        slots[pkg]?.let { return it }
        val taken = slots.values.toSet()
        val next = (0 until maxSlots).firstOrNull { it !in taken } ?: (maxSlots - 1)
        slots[pkg] = next
        sp.edit().putInt("slot:$pkg", next).apply()
        return next
    }

    fun assignedPackages(): Map<String, Int> {
        loadSlots()
        return slots.toMap()
    }

    fun forgetSlots() {
        loadSlots()
        sp.edit().apply { slots.keys.forEach { remove("slot:$it") } }.apply()
        slots.clear()
    }

    private fun loadSlots() {
        if (slotsLoaded) return
        synchronized(slots) {
            if (slotsLoaded) return
            sp.all.forEach { (k, v) ->
                if (k.startsWith("slot:") && v is Int) slots[k.removePrefix("slot:")] = v
            }
            slotsLoaded = true
        }
    }

    private companion object {
        val slots = ConcurrentHashMap<String, Int>()

        @Volatile
        var slotsLoaded = false
    }
}
