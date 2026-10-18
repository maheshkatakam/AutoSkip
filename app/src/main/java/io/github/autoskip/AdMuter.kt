package io.github.autoskip

import android.content.Context
import android.media.AudioManager
import android.util.Log

/**
 * Mutes the media stream while an ad is on screen and puts it back afterwards.
 *
 * It uses a real mute (ADJUST_MUTE), not "set volume to zero", so Android keeps your
 * volume level and unmuting restores exactly what you had. Every way out unmutes:
 * the ad ending, YouTube closing, the setting or the service being switched off, a
 * 3-minute ceiling on any single mute, and a process restart (the flag is on disk).
 * Pressing a volume key during an ad unmutes too, and the muter then leaves that ad alone.
 */
class AdMuter(context: Context, private val prefs: Prefs) {

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var mutedAt = 0L

    /** Set when you unmuted an ad yourself or the ceiling hit; cleared when the ad ends. */
    private var holdOff = false

    fun update(adActive: Boolean, now: Long) {
        if (!prefs.muteAds || !prefs.skipEnabled) {
            release("setting off")
            return
        }
        if (!adActive) {
            holdOff = false
            release("ad ended")
            return
        }
        if (prefs.mutedByUs) {
            if (!isMuted()) {
                // You pressed a volume key during the ad. Respect that for the rest of it.
                prefs.mutedByUs = false
                holdOff = true
                log("volume changed by user; leaving this ad alone")
            } else if (now - mutedAt > MAX_MUTE_MS) {
                release("ceiling reached")
                holdOff = true
            }
            return
        }
        if (holdOff || isMuted()) return // already silent, or you chose to hear it
        if (adjust(AudioManager.ADJUST_MUTE)) {
            prefs.mutedByUs = true
            mutedAt = now
            log("muted for ad")
        }
    }

    /** Unmute if (and only if) this app was the one that muted. Safe to call any time. */
    fun release(reason: String) {
        if (!prefs.mutedByUs) return
        if (isMuted()) adjust(AudioManager.ADJUST_UNMUTE)
        prefs.mutedByUs = false
        mutedAt = 0L
        log("unmuted ($reason)")
    }

    private fun isMuted(): Boolean = audio?.isStreamMute(AudioManager.STREAM_MUSIC) ?: false

    private fun adjust(direction: Int): Boolean = try {
        audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        audio != null
    } catch (e: SecurityException) {
        Log.w(TAG, "could not change media volume", e)
        false
    }

    private fun log(msg: String) {
        if (prefs.verboseLogging) Log.d(TAG, msg)
    }

    companion object {
        private const val TAG = "AutoSkip"
        private const val MAX_MUTE_MS = 3 * 60 * 1000L
    }
}
