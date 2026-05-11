package com.soojiso.autofill

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service that types text into whichever input field
 * the user currently has focused in any app. Triggered via static
 * calls from the floating button service.
 */
class AutoFillAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to events — we only use this service
        // to get a handle on window content when the user taps a button.
    }

    override fun onInterrupt() {
        // no-op
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * Find the currently-focused input node in any window and set its
     * text to [value]. Returns true on success.
     */
    fun fillFocusedInput(value: String): Boolean {
        val node = findFocusedInputNode() ?: return false

        return try {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    value
                )
            }
            val ok = node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                args
            )
            node.recycle()
            ok
        } catch (t: Throwable) {
            false
        }
    }

    private fun findFocusedInputNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val inputFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (inputFocus != null && inputFocus.isEditable) {
            return inputFocus
        }
        return findEditableInTree(root).also {
            if (it !== inputFocus) inputFocus?.recycle()
        }
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

        /**
         * Type the next item from the saved list into the focused input.
         * Only consumes the item if the fill actually succeeds.
         */
        fun triggerFillNextFromList(): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            val items = ListRepository.getItems(svc)
            if (items.isEmpty()) return TriggerResult.EMPTY_LIST
            val next = items.first()
            val ok = svc.fillFocusedInput(next)
            return if (ok) {
                ListRepository.saveItems(svc, items.drop(1))
                TriggerResult.SUCCESS
            } else {
                TriggerResult.NO_INPUT
            }
        }

        /** Fill whatever is currently on the system clipboard. */
        fun triggerFillLatestClipboard(): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            val value = ClipboardHelper.getLatest(svc)
                ?: return TriggerResult.EMPTY_CLIPBOARD
            val ok = svc.fillFocusedInput(value)
            return if (ok) TriggerResult.SUCCESS else TriggerResult.NO_INPUT
        }

        /** Fill a specific pinned value. */
        fun triggerFillPinned(value: String): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            if (value.isEmpty()) return TriggerResult.EMPTY_LIST
            val ok = svc.fillFocusedInput(value)
            return if (ok) TriggerResult.SUCCESS else TriggerResult.NO_INPUT
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
