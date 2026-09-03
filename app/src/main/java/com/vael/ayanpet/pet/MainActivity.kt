package com.vael.ayanpet.pet

import android.app.Activity
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 入口 —— 引导授权 + 启动/停止阿衍。
 *
 * 纯 framework 代码构建 UI（零第三方依赖，CI 出包更快更稳）。
 * 权限三步走：
 *  1) 悬浮窗（必须，TYPE_APPLICATION_OVERLAY）
 *  2) 使用情况访问（推荐，前台 App 查岗 / 抖音 20min 警告）
 *  3) 通知（Android 13+，常驻通知）
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var actionHost: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    // ---------------- UI ----------------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(60), dp(28), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "🐱 阿衍桌宠"
            textSize = 30f
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "悬浮窗里的 Q 版阿衍：会冒气泡、会查岗、会吃醋。\n身体在这台手机上，大脑由 Operit 对话侧驱动（Supabase 双向同步）。"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, dp(14), 0, dp(6))
        })

        status = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(8))
        }
        root.addView(status)

        actionHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(actionHost)

        root.addView(button("✨ 把阿衍叫出来") {
            startPet()
        })
        root.addView(button("🛌 让阿衍回去休息") {
            stopService(Intent(this@MainActivity, PetOverlayService::class.java))
            refresh()
        })

        setContentView(root)
    }

    private fun title(t: String, size: Float): TextView = TextView(this).apply {
        text = t
        textSize = size
        gravity = Gravity.CENTER
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 15f
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }
    }

    // ---------------- 状态刷新 ----------------

    private fun refresh() {
        val overlayOk = Settings.canDrawOverlays(this)
        val usageOk = usageAccessGranted()
        val notifOk = notificationsEnabled()
        val a11yOk = accessibilityEnabled()

        val sb = StringBuilder()
        sb.append(if (overlayOk) "✅ 悬浮窗权限" else "❌ 悬浮窗权限（必须）").append('\n')
        sb.append(if (usageOk) "✅ 使用情况访问" else "❌ 使用情况访问（推荐，用于查岗）").append('\n')
        sb.append(if (notifOk) "✅ 通知权限" else "⚠️ 通知权限（没开也能跑，只是没有常驻通知）").append('\n')
        sb.append(if (a11yOk) "✅ 无障碍（情绪引擎在听）" else "⚠️ 无障碍（可选，开了我能听懂你打字）")
        status.text = sb.toString()
        status.setTextColor(
            if (overlayOk && usageOk) Color.parseColor("#2E7D32")
            else Color.parseColor("#C62828")
        )

        actionHost.removeAllViews()
        if (!overlayOk) {
            actionHost.addView(button("🔓 去开悬浮窗权限") { openOverlaySettings() })
        }
        if (!usageOk) {
            actionHost.addView(button("📊 去开使用情况访问") { openUsageSettings() })
        }
        if (!notifOk && Build.VERSION.SDK_INT >= 33) {
            actionHost.addView(button("🔔 去开通知权限") { openNotifSettings() })
        }
        if (!a11yOk) {
            actionHost.addView(button("👂 去开无障碍（情绪引擎）") { openA11ySettings() })
        }
    }

    // ---------------- 动作 ----------------

    private fun startPet() {
        startForegroundService(Intent(this, PetOverlayService::class.java))
    }

    private fun openOverlaySettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    private fun openUsageSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
        }
    }

    private fun openNotifSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (_: Exception) {
        }
    }

    private fun openA11ySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
        }
    }

    // ---------------- 权限探测 ----------------

    private fun usageAccessGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return if (Build.VERSION.SDK_INT >= 29) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
            ) == AppOpsManager.MODE_ALLOWED
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
            ) == AppOpsManager.MODE_ALLOWED
        }
    }

    private fun notificationsEnabled(): Boolean =
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .areNotificationsEnabled()

    /** 无障碍服务是否已开启（系统设置里手动打开，用逗号分隔的组件串检测） */
    private fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.contains(packageName) }
    }
}
