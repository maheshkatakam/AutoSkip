package io.github.autoskip

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Mutes and unmutes the phone's media volume (STREAM_MUSIC), the stream YouTube and
 * YouTube Music play on. Needs no permission: only the ringer and notification streams can
 * touch Do Not Disturb, and those are never changed.
 */
class StreamMusicMute(context: Context) : MediaMute {

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    override fun isMuted(): Boolean = audio?.isStreamMute(AudioManager.STREAM_MUSIC) ?: false

    override fun setMuted(muted: Boolean): Boolean = try {
        val direction = if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        audio != null
    } catch (e: SecurityException) {
        Log.w("AutoSkip", "could not change media volume", e)
        false
    }
}
