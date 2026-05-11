package com.soojiso.autofill

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Accessibility service that types text into input fields of the
 * foreground app.
 *
 * Three fill entry points, one per floating button:
 *  - [triggerFillSlot1]    — consumes from the IMEI-style list
 *  - [triggerFillSlot2]    — cycles through a persistent 2nd list
 *  - [triggerFillPassword] — cycles through a persistent password list
 *
 * We walk getWindows() in order, skipping IME/system/overlay
 * windows, so overlays from this app or the on-screen keyboard
 * don't confuse the "which window is frontmost?" question.
 *
 * Editable enumeration is a pre-order traversal of the app window's
 * view tree, which approximates visible top-to-bottom order — so
 * "slot 1" is almost always the visually topmost input, "slot 2"
 * the next, and so on.
 */
class AutoFillAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* unused */ }
    override fun onInterrupt() { /* no-op */ }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * Returns the root node of the frontmost app window, skipping
     * the IME, system UI, and our own accessibility overlays.
     */
    private fun frontmostAppRoot(): AccessibilityNodeInfo? {
        val ws: List<AccessibilityWindowInfo> = try {
            windows?.filterNotNull().orEmpty()
        } catch (_: Throwable) {
            emptyList()
        }

        for (w in ws) {
            when (w.type) {
                AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                AccessibilityWindowInfo.TYPE_SYSTEM,
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> continue
            }
            val root = w.root ?: continue
            return root
        }
        return rootInActiveWindow
    }

    /**
     * Collect editable nodes under [root] in pre-order, which
     * approximates top-to-bottom visual order.
     *
     * Caller owns the returned nodes and must recycle the ones it
     * doesn't use.
     */
    private fun collectEditables(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        walk(root, out)
        return out
    }

    private fun walk(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (node.isEditable && node.isVisibleToUser) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            walk(node.getChild(i), out)
        }
    }

    /**
     * Fill the Nth (1-indexed) editable field of the frontmost app
     * with [value]. Returns true on success.
     */
    fun fillNthInput(index1Based: Int, value: String): Boolean {
        if (index1Based < 1) return false
        val root = frontmostAppRoot() ?: return false
        val editables = collectEditables(root)

        if (editables.isEmpty() || editables.size < index1Based) {
            editables.forEach { it.recycle() }
            return false
        }

        val target = editables[index1Based - 1]
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value
            )
        }
        val ok = try {
            target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (_: Throwable) {
            false
        }

        // Recycle every collected node (including the target)
        editables.forEach { it.recycle() }
        return ok
    }

    /** Returns how many editable fields the frontmost app currently exposes. */
    fun countInputs(): Int {
        val root = frontmostAppRoot() ?: return 0
        val editables = collectEditables(root)
        val n = editables.size
        editables.forEach { it.recycle() }
        return n
    }

    companion object {
        @Volatile private var instance: AutoFillAccessibilityService? = null

        fun isRunning(): Boolean = instance != null
        fun get(): AutoFillAccessibilityService? = instance

        /**
         * ① Fill: pop the next item from `items` and type it into
         * slot 1. Only consumes the list item if the fill actually
         * succeeds, and records [ListRepository.lastConsumed] so the
         * Keep button can promote it.
         */
        fun triggerFillSlot1(): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            val items = ListRepository.getItems(svc)
            if (items.isEmpty()) return TriggerResult.EMPTY_LIST
            val next = items.first()

            val n = svc.countInputs()
            if (n == 0) return TriggerResult.NO_INPUT
            if (n < 1) return TriggerResult.NOT_ENOUGH_INPUTS

            val ok = svc.fillNthInput(1, next)
            return if (ok) {
                ListRepository.saveItems(svc, items.drop(1))
                ListRepository.setLastConsumed(svc, next)
                TriggerResult.SUCCESS
            } else {
                TriggerResult.NO_INPUT
            }
        }

        /**
         * ② Fill: cycle to the next value of `list2` and type it
         * into slot 2. The list is persistent — nothing is removed.
         */
        fun triggerFillSlot2(): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            val value = ListRepository.takeNextList2(svc)
                ?: return TriggerResult.EMPTY_LIST

            val n = svc.countInputs()
            if (n == 0) return TriggerResult.NO_INPUT
            if (n < 2) return TriggerResult.NOT_ENOUGH_INPUTS

            val ok = svc.fillNthInput(2, value)
            return if (ok) TriggerResult.SUCCESS else TriggerResult.NO_INPUT
        }

        /**
         * 🔒 Fill: cycle to the next value of the `password` list
         * and type it into slot 3. Password list is persistent.
         */
        fun triggerFillPassword(): TriggerResult {
            val svc = instance ?: return TriggerResult.NOT_RUNNING
            val value = ListRepository.takeNextPassword(svc)
                ?: return TriggerResult.EMPTY_LIST

            val n = svc.countInputs()
            if (n == 0) return TriggerResult.NO_INPUT
            if (n < 3) return TriggerResult.NOT_ENOUGH_INPUTS

            val ok = svc.fillNthInput(3, value)
            return if (ok) TriggerResult.SUCCESS else TriggerResult.NO_INPUT
        }
    }

    enum class TriggerResult {
        SUCCESS,
        NO_INPUT,
        NOT_ENOUGH_INPUTS,
        EMPTY_LIST,
        NOT_RUNNING
    }
}
