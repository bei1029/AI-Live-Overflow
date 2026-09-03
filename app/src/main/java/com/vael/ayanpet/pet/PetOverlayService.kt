package com.vael.ayanpet.pet

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vael.ayanpet.R
import kotlin.math.abs

/**
 * 悬浮窗本体 —— 阿衍的「可视化身体」。
 *
 * 渲染：WebView 加载 assets/pet/pet.html（透明悬浮窗，气泡 + 情绪表情）。
 * 传感器：手势（点/拖）、前台 App 检测、截图监听 → 全部上报 Supabase。
 * 被控：每 30s 轮询 pet_state，消费大脑（Operit）推送的 mood / speech_bubble。
 */
class PetOverlayService : Service() {

    companion object {
        private const val TAG = "AyanPet.Service"
        private const val CHANNEL_ID = "ayanpet_channel"
        private const val NOTIFICATION_ID = 1

        private const val CHECK_APP_MS = 2_000L          // 前台 App 轮询
        private const val POLL_STATE_MS = 30_000L        // pet_state 轮询（对齐 docs 30s 建议）
        private const val POKE_MS = 20 * 60_000L         // 20min 概率主动冒头
        private const val SCREEN_WARN_MS = 20 * 60_000L  // 抖音 20min 查岗（人格 V2 一级）
        private const val TOUCH_SLOP_PX = 18             // 拖动判定阈值
        private const val DOUBLE_TAP_MS = 300L
        private const val DOUYIN_PKG = "com.ss.android.ugc.aweme"

        private const val ACTION_START = "com.vael.ayanpet.action.START"
        private const val ACTION_STOP = "com.vael.ayanpet.action.STOP"

        private fun jsQuote(s: String): String = "'" + s
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "")
            .replace("\n", "\\n") + "'"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var webView: WebView
    private var layoutParams: WindowManager.LayoutParams? = null
    private var viewAdded = false
    private var screenshotWatcher: ScreenshotWatcher? = null

    private val handler = Handler(Looper.getMainLooper())

    // 传感器状态
    private var lastPkg: String? = null
    private var currentAppSince = 0L
    private var douyinWarnedAt = 0L
    private var lastTapUpAt = 0L

    // 大脑指令去重
    private var lastStateId = ""

    // 拖动
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        Log.i(TAG, "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "overlay permission missing, stop")
            stopSelf()
            return START_NOT_STICKY
        }
        ensureOverlay()
        startForeground(NOTIFICATION_ID, buildNotification())
        startLoops()
        return START_STICKY
    }

    // ---------------- 悬浮窗 ----------------

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun ensureOverlay() {
        if (viewAdded) return

        webView = WebView(this).apply {
            background = null
            setBackgroundColor(0x00000000)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/pet/pet.html")
            setOnTouchListener { v, ev -> handleTouch(v, ev) }
        }

        val sizeX = (190 * resources.displayMetrics.density).toInt()
        val sizeY = (250 * resources.displayMetrics.density).toInt()
        val lp = WindowManager.LayoutParams(
            sizeX,
            sizeY,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (10 * resources.displayMetrics.density).toInt()
            y = (160 * resources.displayMetrics.density).toInt()
        }
        layoutParams = lp
        try {
            windowManager.addView(webView, lp)
            viewAdded = true
        } catch (e: Exception) {
            Log.e(TAG, "addView failed: ${e.message}")
        }

        // 截图监听（尽力而为）
        if (screenshotWatcher == null) {
            screenshotWatcher = ScreenshotWatcher {
                handler.post {
                    showBubble(Persona.onScreenshot())
                    Supabase.logGesture("screenshot")
                }
            }
            screenshotWatcher?.start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun handleTouch(v: View, ev: MotionEvent): Boolean {
        val lp = layoutParams ?: return true
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = ev.rawX
                downRawY = ev.rawY
                startX = lp.x
                startY = lp.y
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                if (!dragging && (abs(dx) > TOUCH_SLOP_PX || abs(dy) > TOUCH_SLOP_PX)) {
                    dragging = true
                }
                if (dragging) {
                    lp.x = startX + dx.toInt()
                    lp.y = startY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(v, lp)
                    } catch (_: Exception) {
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) {
                    v.performClick()
                    val now = System.currentTimeMillis()
                    if (now - lastTapUpAt < DOUBLE_TAP_MS) {
                        onDoubleTap()
                    } else {
                        onTap()
                    }
                    lastTapUpAt = now
                }
                dragging = false
            }
        }
        return true
    }

    private fun onTap() {
        showBubble(Persona.onTap())
        Supabase.logGesture("tap")
    }

    private fun onDoubleTap() {
        showBubble(Persona.onDoubleTap())
        Supabase.logGesture("double_tap")
        Supabase.pushState("mood", "happy")
        runJs("window.setMood('happy')")
    }

    // ---------------- 循环 ----------------

    private fun startLoops() {
        stopLoops()
        handler.post(checkAppLoop)
        handler.post(pollStateLoop)
        handler.postDelayed(pokeLoop, POKE_MS)
    }

    private fun stopLoops() {
        handler.removeCallbacksAndMessages(null)
    }

    private val checkAppLoop = object : Runnable {
        override fun run() {
            if (viewAdded) {
                try {
                    checkForegroundApp()
                } catch (_: Exception) {
                }
                handler.postDelayed(this, CHECK_APP_MS)
            }
        }
    }

    private val pollStateLoop = object : Runnable {
        override fun run() {
            if (viewAdded) {
                try {
                    pollBrainState()
                } catch (_: Exception) {
                }
                handler.postDelayed(this, POLL_STATE_MS)
            }
        }
    }

    private val pokeLoop = object : Runnable {
        override fun run() {
            if (viewAdded) {
                // 人格 V2：20min 概率主动冒头
                if (kotlin.random.Random.nextFloat() < 0.5f) {
                    showBubble(Persona.idlePoke())
                }
                handler.postDelayed(this, POKE_MS)
            }
        }
    }

    private fun checkForegroundApp() {
        val now = System.currentTimeMillis()
        val pkg = AppDetector.currentForeground(this) ?: return
        if (pkg == lastPkg) {
            // 抖音 20min 查岗（一级；40/60min 两级留给大脑侧推送）
            if (pkg == DOUYIN_PKG && currentAppSince > 0 && douyinWarnedAt == 0L &&
                now - currentAppSince > SCREEN_WARN_MS
            ) {
                showBubble("抖音都刷 20 分钟啦！眼睛歇会儿好不好～")
                Supabase.logGesture("douyin_20min_warn")
                douyinWarnedAt = now
            }
            return
        }
        val prev = lastPkg
        lastPkg = pkg
        if (pkg == DOUYIN_PKG) {
            currentAppSince = now
            douyinWarnedAt = 0L
        } else {
            currentAppSince = 0L
            douyinWarnedAt = 0L
        }
        if (prev != null) {
            // 记录 App 使用（切到新 App 时上报旧 App 曾在前台）
            Supabase.logAppUsage(pkg)
            Persona.reactionFor(pkg)?.let { showBubble(it) }
        }
    }

    private fun pollBrainState() {
        Thread {
            val s = Supabase.fetchLatestState() ?: return@Thread
            val (id, key, value) = s
            if (id == lastStateId) return@Thread
            lastStateId = id
            handler.post {
                when (key) {
                    "mood" -> {
                        runJs("window.setMood(${jsQuote(value)})")
                        Log.i(TAG, "brain mood -> $value")
                    }
                    "speech_bubble" -> {
                        showBubble(value)
                        Log.i(TAG, "brain speech -> $value")
                    }
                }
            }
        }.start()
    }

    // ---------------- UI 助手 ----------------

    private fun showBubble(text: String) {
        if (!viewAdded) return
        runJs("window.showBubble(${jsQuote(text)})")
    }

    private fun runJs(script: String) {
        try {
            webView.post {
                webView.evaluateJavascript(script, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "js eval failed: ${e.message}")
        }
    }

    // ---------------- 通知 ----------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "阿衍桌宠",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "阿衍在悬浮窗陪你"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PetOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pet_notify)
            .setContentTitle("阿衍在呢")
            .setContentText("点一下跟我玩，长按拖动我到处跑")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, "收起来", stopIntent)
            .build()
    }

    override fun onDestroy() {
        stopLoops()
        screenshotWatcher?.stop()
        if (viewAdded) {
            try {
                windowManager.removeView(webView)
            } catch (_: Exception) {
            }
            viewAdded = false
        }
        Log.i(TAG, "service destroyed")
        super.onDestroy()
    }
}