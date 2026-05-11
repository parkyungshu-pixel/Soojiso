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
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs

/**
 * Foreground service that shows a small draggable panel with three
 * buttons on top of every app:
 *
 *  ①  Fill 1 — fill the next list item into the FIRST input field
 *  ②  Fill 2 — fill the next list item into the SECOND input field
 *  ★  Keep  — move the most recently-consumed item to the Keep list
 *
 * All button taps delegate to [AutoFillAccessibilityService] for the
 * actual typing / list manipulation, and show a toast with the
 * outcome.
 */
class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startAsForeground()
        showFloatingPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeFloatingPanel()
        super.onDestroy()
    }

    // ---------------- Foreground notification ----------------

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
            Intent(this, FloatingButtonService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
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

    // ---------------- Floating panel ----------------

    @Suppress("ClickableViewAccessibility")
    private fun showFloatingPanel() {
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

        view.findViewById<View>(R.id.btn_fill_1)
            .setOnTouchListener(DragClickListener { onFill(1) })
        view.findViewById<View>(R.id.btn_fill_2)
            .setOnTouchListener(DragClickListener { onFill(2) })
        view.findViewById<View>(R.id.btn_keep)
            .setOnTouchListener(DragClickListener { onKeep() })
    }

    private fun removeFloatingPanel() {
        val v = floatingView ?: return
        try { windowManager.removeView(v) } catch (_: Throwable) { }
        floatingView = null
    }

    // ---------------- Button actions ----------------

    private fun onFill(slot: Int) {
        if (!AutoFillAccessibilityService.isRunning()) {
            Toast.makeText(this, R.string.toast_service_off, Toast.LENGTH_SHORT).show()
            return
        }
        val result = AutoFillAccessibilityService.triggerFillNextInto(slot)
        val msg = when (result) {
            AutoFillAccessibilityService.TriggerResult.SUCCESS ->
                getString(R.string.toast_filled_slot, slot)
            AutoFillAccessibilityService.TriggerResult.NO_INPUT ->
                getString(R.string.toast_no_inputs)
            AutoFillAccessibilityService.TriggerResult.NOT_ENOUGH_INPUTS ->
                getString(R.string.toast_not_enough_inputs, slot)
            AutoFillAccessibilityService.TriggerResult.EMPTY_LIST ->
                getString(R.string.toast_empty)
            AutoFillAccessibilityService.TriggerResult.NOT_RUNNING ->
                getString(R.string.toast_service_off)
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun onKeep() {
        val last = ListRepository.getLastConsumed(this)
        if (last.isNullOrEmpty()) {
            Toast.makeText(this, R.string.toast_nothing_to_keep, Toast.LENGTH_SHORT).show()
            return
        }
        ListRepository.appendKeep(this, last)
        // After keeping, clear the marker so the same item can't
        // accidentally be kept twice from back-to-back taps.
        ListRepository.clearLastConsumed(this)
        Toast.makeText(
            this,
            getString(R.string.toast_kept, last),
            Toast.LENGTH_SHORT
        ).show()
    }

    // ---------------- Utils ----------------

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    /** Tap vs drag handler: dragging moves the whole panel. */
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
