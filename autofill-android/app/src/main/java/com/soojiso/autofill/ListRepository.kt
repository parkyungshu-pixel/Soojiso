package com.soojiso.autofill

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence for the AutoFill rotating list and the "Keep" list.
 *
 * - `items` is the main rotating list. Each ⚡ Fill tap removes and
 *   types the first line into the focused input.
 * - `keep` is a parallel list the user builds explicitly by tapping
 *   the Keep button right after a fill. It's an audit log of
 *   values the user wants to remember (e.g. IMEIs they already used
 *   and want to track). Nothing is auto-added here.
 * - `lastConsumed` is the most recent line removed from `items`,
 *   which is what the Keep button promotes to the Keep list when
 *   tapped.
 */
object ListRepository {
    private const val PREFS = "autofill_prefs"
    private const val KEY_ITEMS = "items"
    private const val KEY_KEEP = "keep"
    private const val KEY_LAST_CONSUMED = "last_consumed"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------- Main rotating list ----------

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

    // ---------- Keep list ----------

    fun getKeep(context: Context): List<String> =
        splitNonEmpty(prefs(context).getString(KEY_KEEP, "") ?: "")

    fun saveKeep(context: Context, items: List<String>) {
        prefs(context).edit()
            .putString(KEY_KEEP, items.joinToString("\n"))
            .apply()
    }

    fun saveKeepText(context: Context, text: String) {
        saveKeep(context, splitNonEmpty(text))
    }

    /** Append [value] to the Keep list if not already the most recent entry. */
    fun appendKeep(context: Context, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        val current = getKeep(context).toMutableList()
        // Avoid duplicate back-to-back entries
        if (current.lastOrNull() == trimmed) return
        current.add(trimmed)
        saveKeep(context, current)
    }

    // ---------- Last consumed (for the Keep button) ----------

    fun getLastConsumed(context: Context): String? =
        prefs(context).getString(KEY_LAST_CONSUMED, null)?.takeIf { it.isNotEmpty() }

    fun setLastConsumed(context: Context, value: String?) {
        prefs(context).edit().putString(KEY_LAST_CONSUMED, value ?: "").apply()
    }

    fun clearLastConsumed(context: Context) = setLastConsumed(context, null)

    // ---------- Helpers ----------

    private fun splitNonEmpty(raw: String): List<String> =
        raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}
