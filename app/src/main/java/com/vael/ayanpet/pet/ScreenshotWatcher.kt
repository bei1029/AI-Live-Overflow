package com.vael.ayanpet.pet

import android.os.FileObserver
import android.util.Log
import java.io.File

/**
 * 截图监听 —— FileObserver 盯住常见截图目录。
 *
 * 注：Android 11+ 分区存储下直接路径监听可能受限（公共媒体目录需 MediaStore），
 * 本版为 MVP：能用则用、不能用静默跳过，后续可换成 ContentObserver(MediaStore) 方案。
 */
class ScreenshotWatcher(
    private val onScreenshot: () -> Unit
) {
    private val observers = mutableListOf<FileObserver>()
    private var started = false

    private val candidatePaths = listOf(
        File("/storage/emulated/0/Pictures/Screenshots"),
        File("/storage/emulated/0/DCIM/Screenshots"),
        File("/storage/emulated/0/Screenshots")
    )

    fun start() {
        if (started) return
        for (dir in candidatePaths) {
            if (!dir.isDirectory) continue
            try {
                val observer = object : FileObserver(dir.absolutePath) {
                    override fun onEvent(event: Int, path: String?) {
                        val relevant = (event and (FileObserver.CREATE or FileObserver.MOVED_TO)) != 0
                        if (relevant && path?.endsWith(".png", ignoreCase = true) == true) {
                            Log.i("AyanPet.Screenshot", "new screenshot: $path")
                            onScreenshot()
                        }
                    }
                }
                observer.startWatching()
                observers.add(observer)
                Log.i("AyanPet.Screenshot", "watching ${dir.absolutePath}")
            } catch (e: Exception) {
                Log.w("AyanPet.Screenshot", "cannot watch ${dir.absolutePath}: ${e.message}")
            }
        }
        started = true
    }

    fun stop() {
        observers.forEach { runCatching { it.stopWatching() } }
        observers.clear()
        started = false
    }
}