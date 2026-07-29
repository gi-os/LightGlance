package com.gios.lightglance.notif

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whole-set replacement on every event rather than incremental key bookkeeping.
 * `activeNotifications` is already the truth, and recomputing it is a handful of
 * microseconds, so there is nothing to gain from a diff that can drift.
 */
object NotifStore {
    private val _dots = MutableStateFlow<List<Dot>>(emptyList())
    val dots: StateFlow<List<Dot>> = _dots

    fun publish(dots: List<Dot>) { _dots.value = dots.sortedBy { it.slot } }

    fun total(): Int = _dots.value.sumOf { it.count }

    fun clear() { _dots.value = emptyList() }
}
