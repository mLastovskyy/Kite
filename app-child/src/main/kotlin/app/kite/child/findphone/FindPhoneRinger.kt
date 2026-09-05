package app.kite.child.findphone

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper

/**
 * "Find phone" (M-request): plays a loud alarm so the child device can be located, even in
 * silent/vibrate mode. Uses STREAM_ALARM (which is NOT muted by the ringer's silent mode)
 * at maximum volume and loops the default alarm tone for [DURATION_MS], with a stop control
 * shown by [FindPhoneOverlay]. Ringing itself works offline; the trigger still needs the
 * command to arrive (Realtime / push).
 *
 * Note: full Do-Not-Disturb override needs notification-policy access; the alarm stream
 * already sounds through normal silent/vibrate, which covers the common case.
 */
class FindPhoneRinger(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val overlay = FindPhoneOverlay(context) { stop() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var previousVolume: Int? = null

    val isRinging: Boolean get() = player != null

    // Commands are applied from background coroutines; MediaPlayer, AudioManager and the
    // overlay must touch the main thread, so hop there.
    fun start() = mainHandler.post { startOnMain() }

    fun stop() = mainHandler.post { stopOnMain() }

    private fun startOnMain() {
        if (player != null) return
        previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0,
        )
        val toneUri =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player =
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(context, toneUri)
                isLooping = true
                setOnPreparedListener { start() }
                runCatching { prepareAsync() }
            }
        overlay.show()
        mainHandler.postDelayed({ stopOnMain() }, DURATION_MS)
    }

    private fun stopOnMain() {
        mainHandler.removeCallbacksAndMessages(null)
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        previousVolume?.let { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
        previousVolume = null
        overlay.hide()
    }

    private companion object {
        // Short burst — 5 s per trigger; the parent can ring again if needed.
        const val DURATION_MS = 5_000L
    }
}
