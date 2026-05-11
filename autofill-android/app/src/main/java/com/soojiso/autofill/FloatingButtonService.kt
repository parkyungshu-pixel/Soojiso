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
 * Foreground service that shows a draggable ⚡ button on top of all
 * apps. When tapped, it asks the accessibility service to fill the
 * next item from the saved list into the currently-focused input.
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
        removeFloatingButton()
        super.onDestroy()
    }

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

    @Suppress("ClickableViewAccessibility")
    private fun showFloatingButton() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.floating_button, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Default position: right side, around 40% from top
            val dm = resources.displayMetrics
            x = dm.widthPixels - (dpToPx(72))
            y = (dm.heightPixels * 0.4f).toInt()
        }

        windowManager.addView(view, layoutParams)
        floatingView = view

        val target = view.findViewById<View>(R.id.floating_text)
        target.setOnTouchListener(DragClickListener(
            onClick = { onButtonTapped() }
        ))
    }

    private fun onButtonTapped() {
        if (!AutoFillAccessibilityService.isRunning()) {
            Toast.makeText(this, R.string.toast_service_off, Toast.LENGTH_SHORT).show()
            return
        }
        when (AutoFillAccessibilityService.triggerFill()) {
            AutoFillAccessibilityService.TriggerResult.SUCCESS ->
                Toast.makeText(this, R.string.toast_filled, Toast.LENGTH_SHORT).show()
            AutoFillAccessibilityService.TriggerResult.NO_INPUT ->
                Toast.makeText(this, R.string.toast_no_focus, Toast.LENGTH_SHORT).show()
            AutoFillAccessibilityService.TriggerResult.EMPTY_LIST ->
                Toast.makeText(this, R.string.toast_empty, Toast.LENGTH_SHORT).show()
            AutoFillAccessibilityService.TriggerResult.NOT_RUNNING ->
                Toast.makeText(this, R.string.toast_service_off, Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeFloatingButton() {
        val v = floatingView ?: return
        try {
            windowManager.removeView(v)
        } catch (_: Throwable) { /* ignore */ }
        floatingView = null
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    /**
     * Touch listener that supports both tapping and dragging the
     * floating button across the screen.
     */
    private inner class DragClickListener(
        private val onClick: () -> Unit
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var moved = false
        private val touchSlop = android.view.ViewConfiguration.get(this@FloatingButtonService)
            .scaledTouchSlop

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
                    val dx = (event.rawX - initialTouchX)
                    val dy = (event.rawY - initialTouchY)
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
