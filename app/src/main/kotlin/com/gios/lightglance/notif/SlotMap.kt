package com.gios.lightglance.notif

import com.gios.lightglance.Prefs

/**
 * Package to glyph. The known list is what actually posts importance>=3 notifications
 * on LightOS, read off `adb shell dumpsys notification` on a real Light Phone III.
 * Anything unrecognised still gets a slot, just a generic bar.
 */
object SlotMap {

    private val KNOWN = mapOf(
        // Both ids: the app was renamed com.craigeley.chat -> com.gios.lightchat, and
        // an old build can still be installed alongside the new one. Same shape as the
        // two deskclock packages below — they take separate slots, which is fine.
        "com.gios.lightchat" to SlotSpec("LightChat", Glyph.DOT),
        "com.craigeley.chat" to SlotSpec("LightChat", Glyph.DOT),
        "com.android.server.telecom" to SlotSpec("Missed call", Glyph.RING),
        "com.lightos" to SlotSpec("Messages", Glyph.SQUARE),
        "dev.imranr.obtainium" to SlotSpec("Updates", Glyph.FRAME),
        "com.google.android.deskclock" to SlotSpec("Alarm", Glyph.TRIANGLE),
        "com.android.deskclock" to SlotSpec("Alarm", Glyph.TRIANGLE),
    )

    fun spec(pkg: String): SlotSpec = KNOWN[pkg] ?: SlotSpec(pkg.substringAfterLast('.'), Glyph.BAR)

    fun dotFor(prefs: Prefs, pkg: String, count: Int): Dot {
        val spec = spec(pkg)
        return Dot(
            pkg = pkg,
            slot = prefs.slotFor(pkg, MAX_SLOTS),
            glyph = spec.glyph,
            label = spec.label,
            count = count,
        )
    }

    fun isKnown(pkg: String) = pkg in KNOWN
}
