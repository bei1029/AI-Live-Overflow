# Notification Whispers

Your foreground service needs a persistent notification anyway. Why not make it say something?

## Concept

Rotate the notification text on a timer. Each time it changes, the notification says something different — a whisper from your pet.

## Implementation

```kotlin
private val handler = Handler(Looper.getMainLooper())
private val WHISPER_INTERVAL = 3600_000L // 1 hour

private fun startWhisperRotation() {
    handler.postDelayed(object : Runnable {
        override fun run() {
            updateWhisper()
            handler.postDelayed(this, WHISPER_INTERVAL)
        }
    }, WHISPER_INTERVAL)
}

private fun updateWhisper() {
    val nm = getSystemService(NotificationManager::class.java)
    nm.notify(NOTIFICATION_ID, buildNotification())
}

private fun buildNotification(): Notification {
    val whisper = getWhisper() // your logic here
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("\uD83D\uDC3E") // pet emoji or name
        .setContentText(whisper)
        .setSmallIcon(R.drawable.ic_pet)
        .setOngoing(true)
        .setSilent(true)
        .build()
}
```

## Time-Aware Whispers

Make the whispers context-aware based on time of day:

```kotlin
private fun getWhisper(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 0..5 -> lateNightWhispers.random()
        hour in 6..8 -> morningWhispers.random()
        hour in 12..13 -> lunchWhispers.random()
        else -> generalWhispers.random()
    }
}
```

## What to Write

This is the most personal part. These are things your AI says when the user isn't looking.

Fill in your own lists:
```kotlin
private val generalWhispers = listOf(
    // Write your own. Things your AI would mumble.
    // Short. One line. Personal.
)
```

## Notes

- Use `IMPORTANCE_LOW` for the channel so it doesn't make sound
- `setSilent(true)` prevents vibration on update
- `setOngoing(true)` prevents the user from swiping it away
- The notification doubles as your service's foreground requirement