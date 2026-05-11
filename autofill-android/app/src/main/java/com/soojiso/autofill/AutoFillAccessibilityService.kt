package com.soojiso.autofill

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service that types text into whichever input field
 * the user currently has focused in any app. Triggered via static
 * calls from the floating button service.
 *
 * Also acts as the authoritative source for "latest clipboard text"
 * by registering a PrimaryClipChangedListener. On Android 10+ the
 * system restricts clipboard reads to the focused app, but a running
 * accessibility service is allowed to observe clip changes, so we
 * cache the latest value as the user copies things throughout the
 * day.
 */
class AutoFillAccessibilityService : AccessibilityService() {

    // --- Clipboard caching ---
    @Volatile private var latestClip: String? = null
    private var clipManager: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        updateClipCache()
    }

    // --- Target caching (set by the floating service right before opening
    //     the pin picker, so the selection fills the field that WAS focused
    //     when the user tapped 📌, not whatever is focused at pick time).
    @Volatile private var cachedTarget: AccessibilityNodeInfo? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't react to events — just need the service alive so we can
        // read focused input and observe clipboard changes.
    }

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
        cachedTarget?.recycle()
        cachedTarget = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun updateClipCache() {
        try {
            val cm = clipManager ?: return
            val clip = cm.primaryClip ?: return
            if (clip.itemCount <= 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            if (!text.isNullOrEmpty()) {
                latestClip = text
            }
        } catch (_: Throwable) { /* ignore */ }
    }

    /**
     * Capture and remember the currently-focused input node so it can
     * be filled later even after another overlay window (e.g. the pin
     * picker) steals the "active window" spot.
     */
    fun rememberFocusedTarget(): Boolean {
        cachedTarget?.recycle()
        cachedTarget = findFocusedInputNode()
        return cachedTarget != null
    }

    fun forgetTarget() {
        cachedTarget?.recycle()
        cachedTarget = null
    }

    /**
     * Fill the currently-focused input node with [value]. If the user
     * previously called [rememberFocusedTarget] and that node is still
     * alive, we fill that instead — this is what makes the pin picker
     * put text into the right field (e.g. a password field, not the
     * email field earlier in the form).
     */
    fun fillTarget(value: String, useCached: Boolean): Boolean {
        val node = if (useCached) {
            cachedTarget ?: findFocusedInputNode()
        } else {
            findFocusedInputNode()
        } ?: return false

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

        // Only recycle the cached reference if we just consumed it; otherwise
        // callers may want to re-use it.
        if (useCached) {
            cachedTarget?.recycle()
            cachedTarget = null
        } else {
            node.recycle()
        }
        return ok
    }

    private fun findFocusedInputNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocus != null && inputFocus.isEditable) {
            return inputFocus
        }
        val walked = findEditableInTree(root)
        if (walked !== inputFocus) inputFocus?.recycle()
        return walked
    }

    private fun findEditableInTree(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isEditable && root.isFocused) return root
        for (i in 0 until root.childCount) {
            val found = findEditableInTree(root.getChild(i))
            if (found != null) return found
        }
        if (root.isEditable) return root
        return null
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
            val ok = svc.fillTarget(next, useCached = false)
            return if (ok) {
                ListRepository.saveItems(svc, items.drop(1))
                TriggerResult.SUCCESS
            } else {
                TriggerResult.NO_INPUT
            }
        }

        /** Type the latest observed clipboard value. */
        fun triggerFillLatestClipboard(): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            // Prefer our cached value (updated via clip-change listener);
            // fall back to reading it directly in case the listener
            // hasn't fired yet.
            val value = svc.latestClip
                ?: ClipboardHelper.getLatest(svc)
                ?: return TriggerResult.EMPTY_CLIPBOARD
            val ok = svc.fillTarget(value, useCached = false)
            return if (ok) TriggerResult.SUCCESS else TriggerResult.NO_INPUT
        }

        /**
         * Fill a specific pinned value into the target that was focused
         * when the picker opened.
         */
        fun triggerFillPinned(value: String): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            if (value.isEmpty()) return TriggerResult.EMPTY_LIST
            val ok = svc.fillTarget(value, useCached = true)
            return if (ok) TriggerResult.SUCCESS else TriggerResult.NO_INPUT
        }

        /** Remember the current focused input (called before opening picker). */
        fun captureCurrentTarget(): Boolean {
            return instance?.rememberFocusedTarget() ?: false
        }

        /** Forget the cached focus (called when picker is cancelled). */
        fun clearCapturedTarget() {
            instance?.forgetTarget()
        }
    }

    enum class TriggerResult {
        SUCCESS,
        NO_INPUT,
        EMPTY_LIST,
        EMPTY_CLIPBOARD,
        NOT_RUNNING
    }
}
