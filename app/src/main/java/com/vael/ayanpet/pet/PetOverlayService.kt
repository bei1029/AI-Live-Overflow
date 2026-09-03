package com.vael.ayanpet.pet

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
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
import java.util.Calendar
import kotlin.math.abs
import kotlin.random.Random

/**
 * 悬浮窗本体 —— 阿衍的「可视化身体」。
 *
 * 渲染：WebView 加载 assets/pet/pet.html（透明悬浮窗，气泡 + 情绪表情）。
 * 传感器：手势（点/长按/拖/Fling/连击）、前台 App 检测、截图监听 → 上报 Supabase。
 * 被控：每 30s 轮询 pet_state，消费大脑（Operit）推送的 mood / speech_bubble。
 *
 * 2026-09-03 蓝图增强（#8）：
 *  - 修复：gravity=END 下 x 坐标方向与屏幕 rawX 相反导致拖动反向（lp.x = startX - dx）
 *  - 缩小：窗口 190x250dp → 90x120dp（约 App 图标大小档位，配合 pet.html 同步等比缩小）
 *  - 手势：长按（害羞脸）、Fling 甩出→自动爬回、连击计数（2s 内 3/5/8/10 递进）
 *  - 感知：充电/断电/低电量（ACTION_BATTERY_CHANGED 免权限）、时段问候、喝水提醒（40min+盯梢）
 *  - 情绪：孤独递进五档（5/10/15/20/30min）、好感度 Heat（持久化 + 升级提示）
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

        // —— 蓝图增强 · 常量 ——
        private const val LONG_PRESS_MS = 500L           // 长按判定
        private const val COMBO_WINDOW_MS = 2_000L       // 连击窗口
        private const val LONELY_CHECK_MS = 5 * 60_000L  // 孤独检查周期（5min）
        private const val WATER_INTERVAL_MS = 40 * 60_000L // 喝水提醒间隔
        private const val FLING_DIST_PX = 240f           // Fling 甩出判定位移
        private const val FLING_TIME_MS = 500L           // Fling 甩出判定时长上限
        private const val LOW_BATTERY_PCT = 20           // 低电量阈值
        private const val GREETING_COOLDOWN_MS = 3_600_000L // 时段问候冷却（1h）
        private const val PREFS_HEAT = "ayanpet_heat"
        private const val KEY_HEAT = "heat"

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
    private var downAt = 0L
    private var animToken: Any? = null   // 取消进行中的 Fling 动画

    // —— 蓝图增强 · 运行时状态 ——
    private var tapCount = 0              // 连击计数
    private var firstTapAt = 0L
    private var comboFired = 0            // 已触发过的连击档（3/5/8/10）
    private var lastInteractionAt = 0L    // 孤独计时基准
    private var lonelyNextStep = 5        // 下一孤独档（分钟）
    private var lastWaterAt = 0L          // 上次喝水提醒
    private var lastGreetingAt = 0L       // 上次时段问候
    private var lowWarned = false         // 低电量已提醒
    private var lastBatteryStatus = 0     // 0 未知 / 1 充电 / 2 未充电
    private var heat = 30                 // 好感度 0..100
    private var heatShownLevel = 1        // 已提示过的好感度档（启动静默）

    private val heatPrefs: android.content.SharedPreferences by lazy {
        getSharedPreferences(PREFS_HEAT, Context.MODE_PRIVATE)
    }

    // —— 电池广播（sticky 系统广播，免权限） ——
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (scale > 0) level * 100 / scale else -1
            val charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
            val newStatus = if (charging) 1 else 2
            handler.post {
                // 状态翻转才提醒（首条 sticky 只记录不打扰）
                if (lastBatteryStatus != 0 && newStatus != lastBatteryStatus) {
                    if (newStatus == 1) {
                        showBubble(Persona.batteryCharging())
                        Supabase.logGesture("battery_charging")
                    } else {
                        showBubble(Persona.batteryUnplug())
                        Supabase.logGesture("battery_unplug")
                    }
                }
                lastBatteryStatus = newStatus
                // 低电量提醒（降回 20 以上后重置）
                if (pct in 1..LOW_BATTERY_PCT && !lowWarned) {
                    lowWarned = true
                    showBubble(Persona.batteryLow())
                    Supabase.logGesture("battery_low")
                } else if (pct > LOW_BATTERY_PCT) {
                    lowWarned = false
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        heat = heatPrefs.getInt(KEY_HEAT, 30).coerceIn(0, 100)
        heatShownLevel = heatLevel(heat)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
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
        // 时段问候（启动后 2s 温柔开场，1h 冷却防重）
        handler.postDelayed({
            if (!viewAdded) return@postDelayed
            val now = System.currentTimeMillis()
            if (now - lastGreetingAt >= GREETING_COOLDOWN_MS) {
                lastGreetingAt = now
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                showBubble(Persona.timeGreeting(hour))
            }
        }, 2_000L)
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
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    try {
                        view?.evaluateJavascript(COMPAT_JS, null)
                    } catch (_: Exception) {
                    }
                }
            }
            loadUrl("file:///android_asset/pet/pet.html")
            setOnTouchListener { v, ev -> handleTouch(v, ev) }
        }

        // 缩小：190x250dp → 90x120dp（约 App 图标稍大，配合 pet.html 91x112 CSS px）
        val sizeX = (90 * resources.displayMetrics.density).toInt()
        val sizeY = (120 * resources.displayMetrics.density).toInt()
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
                animToken = null // 打断进行中的 Fling 动画
                downRawX = ev.rawX
                downRawY = ev.rawY
                startX = lp.x
                startY = lp.y
                dragging = false
                downAt = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                if (!dragging && (abs(dx) > TOUCH_SLOP_PX || abs(dy) > TOUCH_SLOP_PX)) {
                    dragging = true
                }
                if (dragging) {
                    // gravity=END：lp.x 为距右缘距离，x 增大=窗口向左，与 rawX 方向相反 → 取负
                    lp.x = startX - dx.toInt()
                    lp.y = startY + dy.toInt() // gravity=TOP：y 与 rawY 同向
                    try {
                        windowManager.updateViewLayout(v, lp)
                    } catch (_: Exception) {
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val now = System.currentTimeMillis()
                if (!dragging) {
                    v.performClick()
                    val pressMs = now - downAt
                    if (pressMs >= LONG_PRESS_MS) {
                        onLongPress()
                    } else if (now - lastTapUpAt < DOUBLE_TAP_MS) {
                        onDoubleTap()
                    } else {
                        onTap()
                    }
                    lastTapUpAt = now
                    registerTap()
                } else {
                    // Fling：快速甩出（位移大 + 时长短）
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (dist > FLING_DIST_PX && now - downAt < FLING_TIME_MS) {
                        doFling(dx, dy)
                    }
                }
                touch() // 任意抬手都算一次互动（刷新孤独计时 + 好感度）
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

    // —— 蓝图增强：长按（害羞脸） ——
    private fun onLongPress() {
        showBubble(Persona.onLongPress())
        runJs("window.setMood('shy')")
        Supabase.pushState("mood", "shy")
        Supabase.logGesture("long_press")
    }

    // —— 蓝图增强：连击计数（2s 窗口内 3/5/8/10 递进） ——
    private fun registerTap() {
        val now = System.currentTimeMillis()
        if (now - firstTapAt > COMBO_WINDOW_MS) {
            tapCount = 1
            firstTapAt = now
            comboFired = 0
        } else {
            tapCount++
        }
        if (comboFired >= 10) return
        when {
            tapCount >= 10 -> {
                comboFired = 10
                showBubble(Persona.comboLine(10))
                runJs("window.setMood('happy')")
                Supabase.logGesture("combo_10")
            }
            tapCount >= 8 && comboFired < 8 -> {
                comboFired = 8
                showBubble(Persona.comboLine(8))
                runJs("window.setMood('happy')")
                Supabase.logGesture("combo_8")
            }
            tapCount >= 5 && comboFired < 5 -> {
                comboFired = 5
                showBubble(Persona.comboLine(5))
                Supabase.logGesture("combo_5")
            }
            tapCount >= 3 && comboFired < 3 -> {
                comboFired = 3
                showBubble(Persona.comboLine(3))
                Supabase.logGesture("combo_3")
            }
        }
    }

    // —— 蓝图增强：Fling 甩出 → 自动爬回原位 ——
    private fun doFling(dx: Float, dy: Float) {
        val lp = layoutParams ?: return
        val v = webView
        val w = lp.width
        val h = lp.height
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val dirX = if (dx > 0f) 1 else if (dx < 0f) -1 else 0
        val dirY = if (dy > 0f) 1 else if (dy < 0f) -1 else 0
        // 甩出落点（完全移出可视区；END gravity 下右甩 x 取负）
        val outX = when (dirX) {
            1 -> -(w + 80)
            -1 -> sw + 80
            else -> lp.x
        }
        val outY = when (dirY) {
            1 -> sh + 80
            -1 -> -(h + 80)
            else -> lp.y
        }
        val originX = startX // 本手势按下时的位置 = 爬回目标
        val originY = startY
        showBubble(Persona.onFlingOut())
        Supabase.logGesture("fling")
        // 阶段1：280ms 飞出
        animateWindow(v, lp.x, lp.y, outX, outY, 280L) {
            showBubble(Persona.onFlingBack())
            runJs("window.setMood('happy')")
            // 阶段2：1000ms 爬回原位
            animateWindow(v, outX, outY, originX, originY, 1000L) {
                runJs("window.setMood('neutral')")
            }
        }
    }

    // —— 蓝图增强：窗口缓动动画（easeOutCubic，token 可打断） ——
    private fun animateWindow(
        v: View,
        fromX: Int, fromY: Int,
        toX: Int, toY: Int,
        durationMs: Long,
        onDone: () -> Unit
    ) {
        val lp = layoutParams ?: return
        val token = Any()
        animToken = token
        val startAt = System.currentTimeMillis()
        val step = object : Runnable {
            override fun run() {
                if (!viewAdded || animToken !== token) return
                val t = (System.currentTimeMillis() - startAt).toFloat() / durationMs
                if (t >= 1f) {
                    lp.x = toX
                    lp.y = toY
                    try {
                        windowManager.updateViewLayout(v, lp)
                    } catch (_: Exception) {
                    }
                    onDone()
                } else {
                    val e = 1f - (1f - t) * (1f - t) * (1f - t)
                    lp.x = (fromX + (toX - fromX) * e).toInt()
                    lp.y = (fromY + (toY - fromY) * e).toInt()
                    try {
                        windowManager.updateViewLayout(v, lp)
                    } catch (_: Exception) {
                    }
                    handler.postDelayed(this, 16L)
                }
            }
        }
        handler.post(step)
    }

    // —— 蓝图增强：互动登记（孤独重置 + 好感度累计） ——
    private fun touch() {
        lastInteractionAt = System.currentTimeMillis()
        lonelyNextStep = 5
        if (heat < 100) heat++
        val lv = heatLevel(heat)
        if (lv > heatShownLevel) {
            heatShownLevel = lv
            showBubble(if (lv >= 3) "好感度爆表！阿衍整颗心都是你的了～" else "好感度上升！阿衍越来越粘你啦～")
        }
        heatPrefs.edit().putInt(KEY_HEAT, heat).apply()
    }

    private fun heatLevel(h: Int): Int = if (h >= 80) 3 else if (h >= 50) 2 else 1

    // ---------------- 循环 ----------------

    private fun startLoops() {
        stopLoops()
        lastWaterAt = System.currentTimeMillis() // 服务启动后 40min 才首次喝水提醒
        lastInteractionAt = System.currentTimeMillis()
        handler.post(checkAppLoop)
        handler.post(pollStateLoop)
        handler.post(lonelyLoop)
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

    // —— 蓝图增强：孤独递进（5/10/15/20/30min 五档，每 5min 检查） ——
    private val lonelyLoop = object : Runnable {
        override fun run() {
            if (viewAdded) {
                try {
                    checkLoneliness()
                } catch (_: Exception) {
                }
                handler.postDelayed(this, LONELY_CHECK_MS)
            }
        }
    }

    private val pokeLoop = object : Runnable {
        override fun run() {
            if (viewAdded) {
                try {
                    // 人格 V2：20min 概率主动冒头（冒头时同步刷新通知文案）
                    if (Random.nextFloat() < 0.5f) {
                        val line = Persona.idlePoke()
                        showBubble(line)
                        updateNotification(line)
                    }
                    // 喝水提醒：40min 间隔；距上次 20~40min 之间 50% 概率二次盯梢
                    val now = System.currentTimeMillis()
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    if (hour in 9..20) {
                        val since = now - lastWaterAt
                        if (since >= WATER_INTERVAL_MS) {
                            lastWaterAt = now
                            showBubble(Persona.waterRemind())
                        } else if (since in 20 * 60_000L until WATER_INTERVAL_MS) {
                            if (Random.nextFloat() < 0.5f) showBubble(Persona.waterRemindAgain())
                        }
                    }
                } catch (_: Exception) {
                }
                handler.postDelayed(this, POKE_MS)
            }
        }
    }

    private fun checkLoneliness() {
        val now = System.currentTimeMillis()
        val gapMin = ((now - lastInteractionAt) / 60_000L).toInt()
        // 好感度缓慢衰减（5min 未互动 -1）
        if (gapMin >= 5 && heat > 0) {
            heat = (heat - 1).coerceAtLeast(0)
            heatPrefs.edit().putInt(KEY_HEAT, heat).apply()
        }
        // 孤独档位递进（lonelyNextStep=45 表示 30min 档已触发完毕，不再打扰）
        if (lonelyNextStep <= 30 && gapMin >= lonelyNextStep) {
            val line = Persona.lonelyLine(lonelyNextStep)
            showBubble(line)
            updateNotification(line)
            runJs("window.setMood('${Persona.moodForLonely(lonelyNextStep)}')")
            Supabase.logGesture("lonely_${lonelyNextStep}min")
            lonelyNextStep += 5
            if (lonelyNextStep > 30) lonelyNextStep = 45
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
    // 兼容桥：若渲染层(pet.html)未自带 window.showBubble，则注入基于 #bubble 元素的实现。
    // 页面自带时（typeof === 'function'）不覆盖，尊重页面自身行为；不动 pet.html 任何字节。
    private val COMPAT_JS = """
        (function () {
          if (typeof window.showBubble === 'function') return;
          window.showBubble = function (text) {
            var b = document.getElementById('bubble');
            if (!b) return;
            b.textContent = text;
            b.classList.add('show');
            if (b._hideTimer) clearTimeout(b._hideTimer);
            b._hideTimer = setTimeout(function () { b.classList.remove('show'); }, 3200);
          };
        })();
    """.trimIndent()

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

    private fun buildNotification(text: String = "点一下跟我玩，长按拖动我到处跑"): Notification {
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
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, "收起来", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        stopLoops()
        screenshotWatcher?.stop()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
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
