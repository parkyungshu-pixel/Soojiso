package com.soojiso.autofill

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence for every list used by the floating buttons.
 *
 * Three lists, three different behaviors:
 *  - `items`  (slot ①) — CONSUMING list. Each ① Fill types the first
 *     line and deletes it. This is the IMEI-style rotating list.
 *  - `list2`  (slot ②) — PERSISTENT cycling list. Each ② Fill types
 *     the next line (advanced by `list2Cursor`) but never deletes.
 *     A single-line list2 therefore reuses the same value forever.
 *  - `password` (slot 🔒) — PERSISTENT cycling list. Same mechanics
 *     as `list2` but intended for password-type values; separated
 *     so a password can be used in parallel with a non-password
 *     2nd-field value.
 *
 * The `keep` list and `lastConsumed` marker only apply to `items`
 * (slot ①) — that's the only list where "used" lines disappear, so
 * it's the only list where the Keep button makes sense.
 */
object ListRepository {
    private const val PREFS = "autofill_prefs"
    private const val KEY_ITEMS = "items"
    private const val KEY_KEEP = "keep"
    private const val KEY_LAST_CONSUMED = "last_consumed"

    private const val KEY_LIST2 = "list2"
    private const val KEY_LIST2_CURSOR = "list2_cursor"
    private const val KEY_PASSWORD = "password"
    private const val KEY_PASSWORD_CURSOR = "password_cursor"

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

    // ---------- List 2 (persistent, cycles) ----------

    fun getList2(context: Context): List<String> =
        splitNonEmpty(prefs(context).getString(KEY_LIST2, "") ?: "")

    fun saveList2(context: Context, items: List<String>) {
        prefs(context).edit()
            .putString(KEY_LIST2, items.joinToString("\n"))
            .apply()
        // Re-normalize the cursor in case it was past the new end.
        normalizeCursor(context, KEY_LIST2_CURSOR, items.size)
    }

    fun saveList2Text(context: Context, text: String) =
        saveList2(context, splitNonEmpty(text))

    /**
     * Returns the next value of list2 and advances the cursor. Does
     * NOT remove anything. If list2 is empty, returns null.
     */
    fun takeNextList2(context: Context): String? =
        takeNextCycling(context, KEY_LIST2, KEY_LIST2_CURSOR)

    // ---------- Password list (persistent, cycles) ----------

    fun getPassword(context: Context): List<String> =
        splitNonEmpty(prefs(context).getString(KEY_PASSWORD, "") ?: "")

    fun savePassword(context: Context, items: List<String>) {
        prefs(context).edit()
            .putString(KEY_PASSWORD, items.joinToString("\n"))
            .apply()
        normalizeCursor(context, KEY_PASSWORD_CURSOR, items.size)
    }

    fun savePasswordText(context: Context, text: String) =
        savePassword(context, splitNonEmpty(text))

    fun takeNextPassword(context: Context): String? =
        takeNextCycling(context, KEY_PASSWORD, KEY_PASSWORD_CURSOR)

    /**
     * Read the next value of a persistent cycling list and bump the
     * cursor to `(cursor + 1) % size`. Returns null if the list is
     * empty.
     */
    private fun takeNextCycling(
        context: Context,
        listKey: String,
        cursorKey: String
    ): String? {
        val raw = prefs(context).getString(listKey, "") ?: ""
        val items = splitNonEmpty(raw)
        if (items.isEmpty()) return null
        val cursor = prefs(context).getInt(cursorKey, 0)
            .coerceIn(0, items.size - 1)
        val value = items[cursor]
        val next = (cursor + 1) % items.size
        prefs(context).edit().putInt(cursorKey, next).apply()
        return value
    }

    /** Keep the cursor sane when the user edits the list behind us. */
    private fun normalizeCursor(context: Context, cursorKey: String, size: Int) {
        val p = prefs(context)
        val cur = p.getInt(cursorKey, 0)
        val clamped = if (size <= 0) 0 else cur.coerceIn(0, size - 1)
        if (clamped != cur) {
            p.edit().putInt(cursorKey, clamped).apply()
        }
    }

    // ---------- Helpers ----------

    private fun splitNonEmpty(raw: String): List<String> =
        raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}
