package com.vael.ayanpet.pet

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍输入监听 —— 情绪引擎 · 通道A。
 *
 * 栀栀在手机任意输入框里打字（微信/小红书/备忘录/Operit…），
 * 这里抓到最新文本 → EmotionTrigger 本地匹配 → 命中就把广播丢给悬浮窗服务。
 *
 * 华为等厂商可能自动杀掉本服务，所以通道B（Supabase 云端推送）做兜底，
 * 两条路共用同一套词库，谁先到谁生效。
 *
 * 2026-09-04 情绪引擎 v1
 */
class AyanAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AyanPet.A11y"

        /** 情绪命中 → 发给 PetOverlayService */
        const val ACTION_EMOTION_INPUT = "com.vael.ayanpet.action.EMOTION_INPUT"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PKG = "pkg"

        /** 无障碍服务连上/断开（通知悬浮窗通道A状态） */
        const val ACTION_A11Y_STATE = "com.vael.ayanpet.action.A11Y_STATE"
        const val EXTRA_ENABLED = "enabled"

        private const val MAX_TEXT = 200          // 匹配只取前 200 字足够
        private const val DEBOUNCE_MS = 1_500L    // 同文本防抖窗口
    }

    private var lastText = ""
    private var lastAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "accessibility connected, channel A alive")
        broadcastState(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        val sb = StringBuilder()
        event.text?.forEach { sb.append(it) }
        val text = sb.toString().trim()
        if (text.isEmpty()) return

        val hit = EmotionTrigger.match(text.take(MAX_TEXT))
        if (!hit.matched) return

        // 防抖：同一句文本 1.5s 内不重复广播（打字过程中会高频触发）
        val now = System.currentTimeMillis()
        if (text == lastText && now - lastAt < DEBOUNCE_MS) return
        lastText = text
        lastAt = now

        Log.i(TAG, "emotion ${hit.level} <- [${hit.word}] in ${event.packageName}")
        val intent = Intent(ACTION_EMOTION_INPUT)
            .setPackage(packageName)            // 显式广播，只进自家门
            .putExtra(EXTRA_TEXT, text)
            .putExtra(EXTRA_PKG, event.packageName?.toString() ?: "")
        sendBroadcast(intent)
    }

    override fun onInterrupt() {
        // 无障碍被系统打断，无需处理
    }

    override fun onDestroy() {
        Log.i(TAG, "accessibility disconnected, channel A lost")
        broadcastState(false)
        super.onDestroy()
    }

    private fun broadcastState(enabled: Boolean) {
        val intent = Intent(ACTION_A11Y_STATE)
            .setPackage(packageName)
            .putExtra(EXTRA_ENABLED, enabled)
        sendBroadcast(intent)
    }
}