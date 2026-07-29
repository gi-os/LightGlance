package com.gios.lightglance.notif

/**
 * The panel is greyscale, so identity cannot be carried by hue. It is carried by
 * shape plus a fixed x-position instead, which survives both the missing colour and
 * the matte panel's contrast loss.
 */
enum class Glyph { DOT, RING, SQUARE, FRAME, BAR, TRIANGLE }

data class SlotSpec(val label: String, val glyph: Glyph)

data class Dot(
    val pkg: String,
    val slot: Int,
    val glyph: Glyph,
    val label: String,
    val count: Int,
)

const val MAX_SLOTS = 6
