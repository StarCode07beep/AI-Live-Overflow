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
import kotlin.random.Random

class PetOverlayService : Service() {

    private lateinit var wm: WindowManager
    private var web: WebView? = null
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    private var lastPkg = ""
    private var dragging = false
    private var moved = false
    private var holding = false
    private var downAt = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0

    private var viewW = 0
    private var viewH = 0
    private var screenW = 0
    private var screenH = 0

    private var dir = -1
    private var phase = 0
    private var wallSide = 1
    private var walking = false
    private var walkEndAt = 0L
    private var nextWalkAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTI_ID, buildNotification())
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
        addPet()
        handler.postDelayed(pollTask, 3000)
        nextWalkAt = System.currentTimeMillis() + 9000
        handler.postDelayed(stepTask, 900)
        handler.postDelayed(decideTask, 1200)
    }

    private fun addPet() {
        val dm = resources.displayMetrics
        val w = (180 * dm.density).toInt()
        val h = (118 * dm.density).toInt()
        viewW = w
        viewH = h

        params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - w + (18 * dm.density).toInt()
            y = dm.heightPixels - h + (18 * dm.density).toInt()
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
                    downAt = System.currentTimeMillis()
                    phase = 0
                    sendJs("setRotate(0)")
                    dragging = true
                    moved = false
                    holding = false
                    stopWalk()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        if (!moved) {
                            moved = true
                            holding = true
                            sendJs("onHold(true)")
                        }
                        params.x = clampX(startX + dx)
                        params.y = clampY(startY + dy)
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
                    val heldMs = System.currentTimeMillis() - downAt
                    dragging = false
                    if (holding) {
                        holding = false
                        sendJs("onHold(false)")
                    }
                    if (abs(dx) <= 10 && abs(dy) <= 10) {
                        if (heldMs > 700) {
                            sendJs("onLongPress()")
                        } else {
                            sendJs("onTap()")
                        }
                    }
                    moved = false
                    nextWalkAt = System.currentTimeMillis() + 4000
                    true
                }
                else -> true
            }
        }

        web = view
        wm.addView(view, params)
    }

    private fun minX(): Int = -(viewW * 0.6).toInt()

    private fun maxX(): Int = screenW - (viewW * 0.92).toInt()

    private fun clampX(v: Int): Int = when {
        v < minX() -> minX()
        v > maxX() -> maxX()
        else -> v
    }

    private fun clampY(v: Int): Int {
        val minY = 0
        val maxY = screenH - (viewH * 0.45).toInt()
        return when {
            v < minY -> minY
            v > maxY -> maxY
            else -> v
        }
    }

    private fun sendJs(code: String) {
        web?.let {
            try {
                it.evaluateJavascript(code, null)
            } catch (t: Throwable) {
            }
        }
    }

    private val stepTask = object : Runnable {
        override fun run() {
            try {
                if (walking && !dragging && web != null) {
                    val speed = (2.5 * resources.displayMetrics.density)
                    val climbSpeed = (3.2 * resources.displayMetrics.density).toInt()
                    when (phase) {
                        0 -> {
                            val nx = params.x + (dir * speed).toInt()
                            if (nx <= minX()) {
                                if (Random.nextFloat() < 0.45f) {
                                    startClimb(-1)
                                } else {
                                    params.x = minX()
                                    dir = 1
                                    sendJs("setFacing(1)")
                                    sendJs("onEdge()")
                                }
                            } else if (nx >= maxX()) {
                                if (Random.nextFloat() < 0.45f) {
                                    startClimb(1)
                                } else {
                                    params.x = maxX()
                                    dir = -1
                                    sendJs("setFacing(-1)")
                                    sendJs("onEdge()")
                                }
                            } else {
                                params.x = nx
                            }
                        }
                        1 -> {
                            val ny = params.y - climbSpeed
                            if (ny <= 0) {
                                params.y = 0
                                phase = 2
                                dir = if (Random.nextBoolean()) -1 else 1
                                sendJs("setRotate(180)")
                                sendJs("setFacing($dir)")
                            } else {
                                params.y = ny
                            }
                        }
                        2 -> {
                            val nx = params.x + (dir * speed).toInt()
                            if (nx <= minX() || nx >= maxX()) {
                                if (nx <= minX()) {
                                    params.x = minX()
                                    wallSide = -1
                                } else {
                                    params.x = maxX()
                                    wallSide = 1
                                }
                                phase = 3
                                sendJs("setRotate(0)")
                                sendJs("setFacing($wallSide)")
                            } else {
                                params.x = nx
                            }
                        }
                        else -> {
                            val ny = params.y + climbSpeed
                            if (ny >= bottomY()) {
                                params.y = bottomY()
                                phase = 0
                                dir = if (wallSide > 0) -1 else 1
                                sendJs("setRotate(0)")
                                sendJs("setFacing($dir)")
                            } else {
                                params.y = ny
                            }
                        }
                    }
                    try {
                        wm.updateViewLayout(web, params)
                    } catch (t: Throwable) {
                    }
                    if (phase != 0) {
                        walkEndAt = System.currentTimeMillis() + 2200
                    }
                    if (System.currentTimeMillis() >= walkEndAt) {
                        stopWalk()
                    }
                }
            } catch (t: Throwable) {
            }
            handler.postDelayed(this, 48)
        }
    }

    private val decideTask = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (!dragging && !walking && now >= nextWalkAt) {
                startWalk()
            }
            handler.postDelayed(this, 1200)
        }
    }

    private fun bottomY(): Int =
        screenH - viewH + (18 * resources.displayMetrics.density).toInt()

    private fun startClimb(side: Int) {
        wallSide = side
        phase = 1
        walkEndAt = System.currentTimeMillis() + 6000
        if (side > 0) {
            params.x = maxX()
            sendJs("setRotate(-10)")
            sendJs("setFacing(-1)")
        } else {
            params.x = minX()
            sendJs("setRotate(10)")
            sendJs("setFacing(1)")
        }
        sendJs("onEdge()")
    }

    private fun startWalk() {
        if (walking) return
        walking = true
        dir = if (Random.nextBoolean()) -1 else 1
        walkEndAt = System.currentTimeMillis() + Random.nextLong(2000, 4200)
        sendJs("setFacing($dir)")
        sendJs("startWalk()")
    }

    private fun stopWalk() {
        if (!walking) return
        walking = false
        sendJs("stopWalk()")
        nextWalkAt = System.currentTimeMillis() + Random.nextLong(7000, 20000)
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