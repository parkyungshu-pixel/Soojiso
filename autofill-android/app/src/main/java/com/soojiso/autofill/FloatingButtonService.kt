package com.soojiso.autofill

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Foreground service that shows a draggable panel of three buttons on
 * top of all apps:
 *
 *  ⚡  — fill the next item from the saved list (consumes it)
 *  📋 — fill whatever is on the system clipboard right now
 *  📌 — open a picker of pinned items (up to the configured max)
 *
 * All three buttons route to [AutoFillAccessibilityService] which
 * re-queries the currently focused input field at fill time. We
 * deliberately no longer cache a node reference when the pin picker
 * opens (that caused stale-node failures on many devices).
 */
class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var pickerView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        showFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removePinPicker()
        removeFloatingButton()
        super.onDestroy()
    }

    // -------- Foreground notification --------

    private fun startAsForeground() {
        val channelId = "autofill_floating"

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingButtonService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openAppIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    // -------- Floating panel --------

    @Suppress("ClickableViewAccessibility")
    private fun showFloatingButton() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.floating_button, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            x = dm.widthPixels - dpToPx(68)
            y = (dm.heightPixels * 0.35f).toInt()
        }

        windowManager.addView(view, layoutParams)
        floatingView = view

        val btnList = view.findViewById<View>(R.id.btn_fill_list)
        val btnClip = view.findViewById<View>(R.id.btn_fill_clipboard)
        val btnPin = view.findViewById<View>(R.id.btn_fill_pinned)

        btnList.setOnTouchListener(DragClickListener { onFillListTapped() })
        btnClip.setOnTouchListener(DragClickListener { onFillClipboardTapped() })
        btnPin.setOnTouchListener(DragClickListener { onFillPinnedTapped() })
    }

    private fun removeFloatingButton() {
        val v = floatingView ?: return
        try { windowManager.removeView(v) } catch (_: Throwable) { }
        floatingView = null
    }

    // -------- Button actions --------

    private fun onFillListTapped() {
        if (!AutoFillAccessibilityService.isRunning()) {
            Toast.makeText(this, R.string.toast_service_off, Toast.LENGTH_SHORT).show()
            return
        }
        showResult(AutoFillAccessibilityService.triggerFillNextFromList())
    }

    private fun onFillClipboardTapped() {
        if (!AutoFillAccessibilityService.isRunning()) {
            Toast.makeText(this, R.string.toast_service_off, Toast.LENGTH_SHORT).show()
            return
        }
        showResult(AutoFillAccessibilityService.triggerFillLatestClipboard())
    }

    private fun onFillPinnedTapped() {
        if (!AutoFillAccessibilityService.isRunning()) {
            Toast.makeText(this, R.string.toast_service_off, Toast.LENGTH_SHORT).show()
            return
        }
        val pins = ListRepository.getPins(this)
        if (pins.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_pins, Toast.LENGTH_SHORT).show()
            return
        }
        // Verify a focused input exists BEFORE opening the picker so we
        // can show a clear "tap an input first" toast instead of opening
        // a useless menu.
        if (!AutoFillAccessibilityService.captureCurrentTarget()) {
            Toast.makeText(this, R.string.toast_no_focus, Toast.LENGTH_SHORT).show()
            return
        }
        showPinPicker(pins)
    }

    private fun showResult(result: AutoFillAccessibilityService.TriggerResult) {
        val msgRes = when (result) {
            AutoFillAccessibilityService.TriggerResult.SUCCESS -> R.string.toast_filled
            AutoFillAccessibilityService.TriggerResult.NO_INPUT -> R.string.toast_no_focus
            AutoFillAccessibilityService.TriggerResult.EMPTY_LIST -> R.string.toast_empty
            AutoFillAccessibilityService.TriggerResult.EMPTY_CLIPBOARD -> R.string.toast_empty_clipboard
            AutoFillAccessibilityService.TriggerResult.NOT_RUNNING -> R.string.toast_service_off
        }
        Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
    }

    // -------- Pin picker overlay --------

    @Suppress("ClickableViewAccessibility")
    private fun showPinPicker(pins: List<String>) {
        removePinPicker()

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.pin_picker, null)
        val listContainer = view.findViewById<LinearLayout>(R.id.pin_list)
        val closeBtn = view.findViewById<TextView>(R.id.pin_close)

        pins.forEachIndexed { index, value ->
            val row = TextView(this).apply {
                text = "${index + 1}. $value"
                setTextColor(0xFFF5EEDC.toInt())
                textSize = 14f
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                setBackgroundResource(R.drawable.pin_item_bg)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener {
                    // Fill FIRST (while the target field is still focused
                    // under the non-focusable picker), then remove the
                    // picker. Re-querying focus happens inside the
                    // accessibility service.
                    val result = AutoFillAccessibilityService.triggerFillPinned(value)
                    removePinPicker()
                    showResult(result)
                }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(4) }
            listContainer.addView(row, lp)
        }

        closeBtn.setOnClickListener { removePinPicker() }

        // FLAG_NOT_FOCUSABLE is crucial: it keeps the user's input field
        // focused in the app below, so accessibility focus lookup still
        // returns the right field when the user taps a pin.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            val estimatedWidth = dpToPx(240)
            val panelX = layoutParams.x
            x = (panelX - estimatedWidth - dpToPx(12)).coerceAtLeast(dpToPx(8))
            y = layoutParams.y.coerceAtMost(dm.heightPixels - dpToPx(260))
        }

        view.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_OUTSIDE) {
                removePinPicker()
                true
            } else false
        }

        windowManager.addView(view, params)
        pickerView = view
    }

    private fun removePinPicker() {
        val v = pickerView ?: return
        try { windowManager.removeView(v) } catch (_: Throwable) { }
        pickerView = null
    }

    // -------- Utils --------

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    /** Tap-or-drag listener. Drags move the whole floating panel. */
    private inner class DragClickListener(
        private val onClick: () -> Unit
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var moved = false
        private val touchSlop =
            android.view.ViewConfiguration.get(this@FloatingButtonService).scaledTouchSlop

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (pickerView != null) removePinPicker()
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        moved = true
                    }
                    if (moved) {
                        layoutParams.x = (initialX + dx).toInt()
                        layoutParams.y = (initialY + dy).toInt()
                        windowManager.updateViewLayout(floatingView, layoutParams)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onClick()
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.soojiso.autofill.STOP_FLOATING"

        fun isRunning(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            return am.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == FloatingButtonService::class.java.name
            }
        }
    }
}
