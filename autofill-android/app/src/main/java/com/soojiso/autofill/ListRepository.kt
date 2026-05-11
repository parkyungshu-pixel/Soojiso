package com.soojiso.autofill

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple list persistence backed by SharedPreferences.
 * Stores the list as newline-separated text, exactly like the
 * original Chrome extension's storage format.
 */
object ListRepository {
    private const val PREFS = "autofill_prefs"
    private const val KEY_ITEMS = "items"

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getItems(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_ITEMS, "") ?: ""
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun saveItems(context: Context, items: List<String>) {
        prefs(context).edit()
            .putString(KEY_ITEMS, items.joinToString("\n"))
            .apply()
    }

    fun saveText(context: Context, text: String) {
        val items = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        saveItems(context, items)
    }

    /** Pop the first item off the list and return it, or null if empty. */
    fun popFirst(context: Context): String? {
        val items = getItems(context).toMutableList()
        if (items.isEmpty()) return null
        val first = items.removeAt(0)
        saveItems(context, items)
        return first
    }

    fun registerListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        prefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }
}
