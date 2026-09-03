package com.vael.ayanpet.pet

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

/**
 * 前台 App 检测 —— 用 UsageStatsManager 探测当前正在用的应用。
 * 需要用户在系统设置里授予「使用情况访问权限」（MainActivity 会引导跳转）。
 * 未授权时静默返回 null，不影响悬浮窗本体。
 */
object AppDetector {
    private const val TAG = "AyanPet.AppDetector"

    fun currentForeground(context: Context): String? = try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 15_000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
        if (stats.isNullOrEmpty()) {
            null
        } else {
            stats
                .filter { it.lastTimeUsed in begin..end }
                .maxByOrNull { it.lastTimeUsed }
                ?.packageName
        }
    } catch (e: Exception) {
        Log.w(TAG, "usage stats not available: ${e.message}")
        null
    }
}