package com.soojiso.autofill

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Accessibility service that types text into whichever input field
 * the user currently has focused in any app.
 *
 * Design notes:
 *  - Focus detection iterates over [getWindows] so we can skip our
 *    own overlay panel and the IME keyboard — otherwise
 *    rootInActiveWindow can return the wrong window and we either
 *    find no editable at all or find the wrong one (e.g. filling the
 *    email field when the user was actually typing in password).
 *  - We deliberately do NOT fall back to "any editable in the tree"
 *    when findFocus() fails. Filling a random field is worse than
 *    doing nothing; the user will see a clear "tap an input first"
 *    toast instead.
 *  - Clipboard reads are allowed for the accessibility service's
 *    own context regardless of app focus, so we always try a fresh
 *    read first and only fall back to the listener-cached value.
 */
class AutoFillAccessibilityService : AccessibilityService() {

    // Clipboard caching via listener (fires when user copies anywhere)
    @Volatile private var latestClip: String? = null
    private var clipManager: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        updateClipCache()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not needed */ }
    override fun onInterrupt() { /* no-op */ }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.addPrimaryClipChangedListener(clipListener)
            clipManager = cm
            updateClipCache()
        } catch (_: Throwable) { /* ignore */ }
    }

    override fun onDestroy() {
        try {
            clipManager?.removePrimaryClipChangedListener(clipListener)
        } catch (_: Throwable) { /* ignore */ }
        clipManager = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun updateClipCache() {
        try {
            val cm = clipManager ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount <= 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            if (!text.isNullOrEmpty()) latestClip = text
        } catch (_: Throwable) { /* ignore */ }
    }

    /**
     * Fresh clipboard read from the accessibility service's own context.
     * The OS allows this regardless of which app currently has focus,
     * because accessibility services are a trusted clipboard reader.
     */
    private fun readClipboardNow(): String? {
        return try {
            val cm = clipManager
                ?: (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?: return null
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            text?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Locate the input field the user is currently typing into.
     *
     * Returns null if no such field exists — callers should surface a
     * "tap an input first" message instead of falling back to a
     * "best guess" field (which historically filled the wrong input
     * on forms with multiple EditTexts).
     */
    fun findFocusedInputNode(): AccessibilityNodeInfo? {
        val candidates: List<AccessibilityWindowInfo> = try {
            windows?.filterNotNull().orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }

        // Top-most first; skip IME, system UI, and our own overlays.
        for (w in candidates) {
            when (w.type) {
                AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                AccessibilityWindowInfo.TYPE_SYSTEM,
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> continue
            }
            val root = w.root ?: continue
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                return focused
            }
        }

        // Last-resort fallback for devices that don't expose windows()
        // reliably. Only trust findFocus — never return an arbitrary
        // editable, since that can fill the wrong field.
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused
        return null
    }

    /**
     * Type [value] into whichever input field is currently focused.
     * Returns true on success.
     */
    fun fillFocused(value: String): Boolean {
        val node = findFocusedInputNode() ?: return false
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value
            )
        }
        val ok = try {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (_: Throwable) {
            false
        }
        node.recycle()
        return ok
    }

    companion object {
        @Volatile
        private var instance: AutoFillAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
        fun get(): AutoFillAccessibilityService? = instance

        /** Type the next item from the saved list. Consumes on success. */
        fun triggerFillNextFromList(): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            val items = ListRepository.getItems(svc)
            if (items.isEmpty()) return TriggerResult.EMPTY_LIST
            val next = items.first()
            val ok = svc.fillFocused(next)
            return if (ok) {
                ListRepository.saveItems(svc, items.drop(1))
                TriggerResult.SUCCESS
            } else {
                TriggerResult.NO_INPUT
            }
        }

        /** Type the latest clipboard value into the focused field. */
        fun triggerFillLatestClipboard(): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            // Always try a fresh read from the a11y service context —
            // more reliable than the listener cache on some OEMs.
            val value = svc.readClipboardNow()
                ?: svc.latestClip
                ?: return TriggerResult.EMPTY_CLIPBOARD
            val ok = svc.fillFocused(value)
            return if (ok) TriggerResult.SUCCESS else TriggerResult.NO_INPUT
        }

        /**
         * Type a specific pinned value. We re-query the focused input at
         * fill time (rather than caching a node reference when the
         * picker opened), because cached AccessibilityNodeInfo handles
         * go stale quickly on many devices.
         */
        fun triggerFillPinned(value: String): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            if (value.isEmpty()) return TriggerResult.EMPTY_LIST
            val ok = svc.fillFocused(value)
            return if (ok) TriggerResult.SUCCESS else TriggerResult.NO_INPUT
        }

        /**
         * No-op today (kept for binary compatibility with the floating
         * service, which calls it defensively). We used to cache a node
         * reference here but that proved unreliable; we now re-query
         * focus at fill time instead.
         */
        fun captureCurrentTarget(): Boolean {
            val svc = instance ?: return false
            // Return true iff there's currently a focused editable input —
            // this way the floating service can still gate "show picker"
            // on "is there somewhere to fill?".
            val node = svc.findFocusedInputNode() ?: return false
            node.recycle()
            return true
        }

        fun clearCapturedTarget() { /* no-op, see captureCurrentTarget */ }
    }

    enum class TriggerResult {
        SUCCESS,
        NO_INPUT,
        EMPTY_LIST,
        EMPTY_CLIPBOARD,
        NOT_RUNNING
    }
}
