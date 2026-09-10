package com.starcode.ai_live_pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class PetOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var web: WebView? = null
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private var lastPkg = ""
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTI_ID, buildNotification())
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addPet()
        handler.postDelayed(pollTask, 3000)
    }

    private fun addPet() {
        val dm = resources.displayMetrics
        val w = (220 * dm.density).toInt()
        val h = (100 * dm.density).toInt()

        params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - w - (6 * dm.density).toInt()
            y = (dm.heightPixels * 0.68).toInt()
        }

        val view = WebView(this).apply {
            setBackgroundColor(0x00000000)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            settings.javaScriptEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mediaPlaybackRequiresUserGesture = false
        }
        view.webViewClient = WebViewClient()
        view.loadUrl("file:///android_asset/pet.html")

        view.setOnTouchListener { touched, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        params.x = startX + dx
                        params.y = startY + dy
                        try {
                            wm.updateViewLayout(touched, params)
                        } catch (t: Throwable) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()
                    if (abs(dx) <= 10 && abs(dy) <= 10) {
                        (touched as WebView).evaluateJavascript("onTap()", null)
                    }
                    true
                }
                else -> true
            }
        }

        web = view
        wm.addView(view, params)
    }

    private val pollTask = object : Runnable {
        override fun run() {
            try {
                val pkg = currentPkg()
                if (pkg.isNotEmpty() && pkg != lastPkg) {
                    lastPkg = pkg
                    web?.evaluateJavascript("switchApp('$pkg')", null)
                }
            } catch (t: Throwable) {
            }
            handler.postDelayed(this, 3000)
        }
    }

    private fun currentPkg(): String {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return ""
        val end = System.currentTimeMillis()
        val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 60000, end) ?: return ""
        var best: UsageStats? = null
        for (s in list) {
            if (best == null || s.lastTimeUsed > best.lastTimeUsed) best = s
        }
        return best?.packageName ?: ""
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "桌宠", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle("人在线")
            .setContentText("正趴在你屏幕上")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        web?.let {
            try {
                wm.removeView(it)
            } catch (t: Throwable) {
            }
        }
        web = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTI_ID = 1024
    }
}
