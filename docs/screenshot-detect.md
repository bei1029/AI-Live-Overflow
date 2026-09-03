# Screenshot Detection

Detect when the user takes a screenshot. Your pet can react — pose, hide, wave, panic, etc.

## How It Works

Android stores screenshots in known directories. A `FileObserver` watches those paths for new files.

## Known Screenshot Paths

```kotlin
private val SCREENSHOT_PATHS = listOf(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        .resolve("Screenshots").absolutePath,
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        .resolve("Screenshots").absolutePath,
    // Huawei-specific
    "/storage/emulated/0/Pictures/Screenshots",
    "/storage/emulated/0/DCIM/Screenshots",
)
```

## Implementation

```kotlin
class ScreenshotObserver(private val webView: WebView?) {
    private val observers = mutableListOf<FileObserver>()

    fun start() {
        for (path in SCREENSHOT_PATHS) {
            val dir = File(path)
            if (!dir.exists()) continue

            val observer = object : FileObserver(dir, CREATE or MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path != null && isImageFile(path)) {
                        onScreenshotDetected()
                    }
                }
            }
            observer.startWatching()
            observers.add(observer)
        }
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".png") ||
               lower.endsWith(".jpg") ||
               lower.endsWith(".jpeg")
    }

    private fun onScreenshotDetected() {
        // Tell the WebView the user just took a screenshot
        Handler(Looper.getMainLooper()).post {
            webView?.evaluateJavascript(
                "window.petEngine && window.petEngine.onScreenshot()", null
            )
        }
    }

    fun stop() {
        observers.forEach { it.stopWatching() }
        observers.clear()
    }
}
```

## Permission

Needs `READ_EXTERNAL_STORAGE` or `READ_MEDIA_IMAGES` (Android 13+) to observe these directories. Alternatively, on Android 14+ you can use `MediaStore` content observer.

## Reaction Ideas

- Pet notices and poses ("cheese!")
- Pet panics and hides
- Pet holds up a sign
- Counter: track how many times the user screenshots the pet

## Notes

- FileObserver callbacks come on a background thread — use Handler to post to main thread before touching WebView
- Some manufacturers put screenshots in non-standard paths. Test on your actual device.
- The observer must be kept alive as long as the service runs. Stop it in `onDestroy`.