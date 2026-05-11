package com.soojiso.autofill

import android.content.ClipboardManager
import android.content.Context

/**
 * Reads the latest item currently on the system clipboard.
 *
 * Important: on Android 10+ the system only allows reading the
 * clipboard while the reading app has focus OR while an
 * AccessibilityService is active. Our accessibility service is
 * always on when the floating button is used, so this works.
 */
object ClipboardHelper {
    fun getLatest(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        val text = clip.getItemAt(0).coerceToText(context)?.toString()
        return text?.takeIf { it.isNotEmpty() }
    }
}
