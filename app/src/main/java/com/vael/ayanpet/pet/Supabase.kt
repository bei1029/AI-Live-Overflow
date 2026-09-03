package com.vael.ayanpet.pet

import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase 同步层 —— 桌宠的「传感器数据出口 + 大脑指令入口」。
 *
 * 架构约束：这里的 key 是 anon key（客户端公开、配 RLS 只允许本机 insert/select），
 * service_role key 绝不下发到客户端，只留在 Operit / 服务端。
 */
object Supabase {
    private const val TAG = "AyanPet.Supabase"
    private const val BASE_URL = "https://ictbchqvjcdszykzvijc.supabase.co"
    private const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImljdGJjaHF2amNkc3p5a3p2aWpjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg0MjkzOTIsImV4cCI6MjEwNDAwNTM5Mn0.ZZLB0q3Wpm8PZpG_B7n04Ju_DHzAzzY1DKDbcBHdJts"

    /** 上报一条手势 / 事件到 gesture_log */
    fun logGesture(type: String, x: Int = 0, y: Int = 0) {
        post("gesture_log", JSONObject()
            .put("gesture_type", type)
            .put("x", x)
            .put("y", y))
    }

    /** 上报一次前台 App 切换到 app_usage */
    fun logAppUsage(packageName: String) {
        post("app_usage", JSONObject().put("package_name", packageName))
    }

    /** 推送一条自身状态（如当前心情）到 pet_state */
    fun pushState(key: String, value: String) {
        post("pet_state", JSONObject()
            .put("state_key", key)
            .put("state_value", value))
    }

    /**
     * 拉取 AI（Operit 侧）最新推送的 pet_state。
     * 返回 Triple(id, state_key, state_value)，取最新一条；网络失败返回 null。
     * （含 id：服务侧 pollBrainState 用它跳过已消费的指令）
     */
    fun fetchLatestState(): Triple<String, String, String>? = try {
        val conn = URL("$BASE_URL/rest/v1/pet_state?select=id,state_key,state_value&order=updated_at.desc&limit=1")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("apikey", ANON_KEY)
        conn.setRequestProperty("Authorization", "Bearer $ANON_KEY")
        if (conn.responseCode in 200..299) {
            val body = conn.inputStream.bufferedReader().readText()
            val arr = org.json.JSONArray(body)
            if (arr.length() > 0) {
                val o = arr.getJSONObject(0)
                Triple(o.getString("id"), o.getString("state_key"), o.getString("state_value"))
            } else null
        } else {
            Log.w(TAG, "GET pet_state -> ${conn.responseCode}")
            null
        }
    } catch (e: Exception) {
        Log.w(TAG, "GET pet_state failed: ${e.message}")
        null
    } finally {
        // conn 已随 try 表达式结束；此处无需额外关闭
    }

    private fun post(table: String, payload: JSONObject) {
        Thread {
            try {
                val conn = URL("$BASE_URL/rest/v1/$table").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("apikey", ANON_KEY)
                conn.setRequestProperty("Authorization", "Bearer $ANON_KEY")
                conn.setRequestProperty("Prefer", "return=minimal")
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                Log.i(TAG, "POST $table -> ${conn.responseCode}")
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "POST $table failed: ${e.message}")
            }
        }.start()
    }
}