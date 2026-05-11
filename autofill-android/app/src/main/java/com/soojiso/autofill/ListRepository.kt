package com.soojiso.autofill

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple persistence backed by SharedPreferences.
 *
 * Stores three things:
 *  - items: the rotating auto-fill list (newline separated)
 *  - pins:  user-curated "favorites" (newline separated, max N)
 *  - max_pins: user-configurable cap on the number of pinned items
 */
object ListRepository {
    private const val PREFS = "autofill_prefs"
    private const val KEY_ITEMS = "items"
    private const val KEY_PINS = "pins"
    private const val KEY_MAX_PINS = "max_pins"
    const val DEFAULT_MAX_PINS = 5

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Rotating list ----------

    fun getItems(context: Context): List<String> =
        splitNonEmpty(prefs(context).getString(KEY_ITEMS, "") ?: "")

    fun saveItems(context: Context, items: List<String>) {
        prefs(context).edit()
            .putString(KEY_ITEMS, items.joinToString("\n"))
            .apply()
    }

    fun saveText(context: Context, text: String) {
        saveItems(context, splitNonEmpty(text))
    }

    // ---------- Pins ----------

    fun getPins(context: Context): List<String> =
        splitNonEmpty(prefs(context).getString(KEY_PINS, "") ?: "")

    fun savePins(context: Context, pins: List<String>) {
        val max = getMaxPins(context)
        val trimmed = pins.take(max)
        prefs(context).edit()
            .putString(KEY_PINS, trimmed.joinToString("\n"))
            .apply()
    }

    fun savePinsText(context: Context, text: String) {
        savePins(context, splitNonEmpty(text))
    }

    /** Adds [value] to the front of the pins list, de-duplicated, respecting max. */
    fun addPinToFront(context: Context, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        val current = getPins(context).filter { it != trimmed }.toMutableList()
        current.add(0, trimmed)
        savePins(context, current)
    }

    fun getMaxPins(context: Context): Int =
        prefs(context).getInt(KEY_MAX_PINS, DEFAULT_MAX_PINS).coerceAtLeast(1)

    fun setMaxPins(context: Context, n: Int) {
        val clamped = n.coerceIn(1, 30)
        prefs(context).edit().putInt(KEY_MAX_PINS, clamped).apply()
        // Also trim pins if the new max is smaller
        val pins = getPins(context)
        if (pins.size > clamped) savePins(context, pins.take(clamped))
    }

    // ---------- Helpers ----------

    private fun splitNonEmpty(raw: String): List<String> =
        raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}
