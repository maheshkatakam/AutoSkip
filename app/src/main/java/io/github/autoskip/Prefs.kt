package io.github.autoskip

import android.content.Context
import android.content.SharedPreferences

/** Settings and counters, shared by the screen and the accessibility service (same process). */
class Prefs(context: Context) {

    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("autoskip", Context.MODE_PRIVATE)

    /** Master switch. When off, the service stays enabled but does nothing. */
    var skipEnabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Seconds to wait after the Skip button appears before pressing it (0 = at once). */
    var delaySeconds: Int
        get() = sp.getInt(KEY_DELAY, 0).coerceIn(0, MAX_DELAY_SECONDS)
        set(v) = sp.edit().putInt(KEY_DELAY, v.coerceIn(0, MAX_DELAY_SECONDS)).apply()

    /** Mute media volume while an ad is on screen, including ads with no Skip button. */
    var muteAds: Boolean
        get() = sp.getBoolean(KEY_MUTE, false)
        set(v) = sp.edit().putBoolean(KEY_MUTE, v).apply()

    /** Write what the service sees to logcat (tag "AutoSkip"), for fixing it after YouTube updates. */
    var verboseLogging: Boolean
        get() = sp.getBoolean(KEY_VERBOSE, false)
        set(v) = sp.edit().putBoolean(KEY_VERBOSE, v).apply()

    val skippedCount: Int get() = sp.getInt(KEY_SKIPPED, 0)
    val lastSkipAt: Long get() = sp.getLong(KEY_LAST_SKIP, 0L)

    fun recordSkip(nowWallClock: Long) {
        sp.edit()
            .putInt(KEY_SKIPPED, skippedCount + 1)
            .putLong(KEY_LAST_SKIP, nowWallClock)
            .apply()
    }

    fun resetStats() {
        sp.edit().remove(KEY_SKIPPED).remove(KEY_LAST_SKIP).apply()
    }

    /**
     * True while this app is holding the media stream muted. Written to disk with commit()
     * so that if the process is killed mid-ad, the next start knows to unmute.
     */
    var mutedByUs: Boolean
        get() = sp.getBoolean(KEY_MUTED_BY_US, false)
        set(v) {
            sp.edit().putBoolean(KEY_MUTED_BY_US, v).commit()
        }

    companion object {
        const val KEY_ENABLED = "skip_enabled"
        const val KEY_DELAY = "delay_seconds"
        const val KEY_MUTE = "mute_ads"
        const val KEY_VERBOSE = "verbose_logging"
        const val KEY_SKIPPED = "skipped_count"
        const val KEY_LAST_SKIP = "last_skip_at"
        const val KEY_MUTED_BY_US = "muted_by_us"
        const val MAX_DELAY_SECONDS = 10
    }
}
