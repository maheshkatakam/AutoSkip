package io.github.autoskip

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo

/** Settings and counters, shared by the screen and the accessibility service (same process). */
class Prefs(context: Context) : MuteMemory {

    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("autoskip", Context.MODE_PRIVATE)

    /** Debug builds (Android Studio, the emulator tests) log by default; releases don't. */
    private val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

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
        get() = sp.getBoolean(KEY_VERBOSE, debuggable)
        set(v) = sp.edit().putBoolean(KEY_VERBOSE, v).apply()

    /** Per-app switch, so you can use AutoSkip in YouTube only, YouTube Music only, or both. */
    fun isAppEnabled(app: WatchedApp): Boolean = sp.getBoolean(appEnabledKey(app), true)

    fun setAppEnabled(app: WatchedApp, enabled: Boolean) {
        sp.edit().putBoolean(appEnabledKey(app), enabled).apply()
    }

    /** All ads skipped, in every app. */
    val skippedCount: Int get() = sp.getInt(KEY_SKIPPED, 0)
    val lastSkipAt: Long get() = sp.getLong(KEY_LAST_SKIP, 0L)

    /**
     * Ads skipped in one app. Version 1.0 kept only the total, and only skipped in YouTube,
     * so YouTube's figure is the total minus every other app's: older skips land there.
     */
    fun skippedCount(app: WatchedApp): Int {
        if (app != WatchedApp.YOUTUBE) return sp.getInt(skippedKey(app), 0)
        val others = WatchedApp.entries.filter { it != WatchedApp.YOUTUBE }.sumOf { sp.getInt(skippedKey(it), 0) }
        return (skippedCount - others).coerceAtLeast(0)
    }

    fun recordSkip(app: WatchedApp, nowWallClock: Long) {
        val edit = sp.edit()
            .putInt(KEY_SKIPPED, skippedCount + 1)
            .putLong(KEY_LAST_SKIP, nowWallClock)
        if (app != WatchedApp.YOUTUBE) edit.putInt(skippedKey(app), sp.getInt(skippedKey(app), 0) + 1)
        edit.apply()
    }

    fun resetStats() {
        val edit = sp.edit().remove(KEY_SKIPPED).remove(KEY_LAST_SKIP)
        WatchedApp.entries.forEach { edit.remove(skippedKey(it)) }
        edit.apply()
    }

    override val muteEnabled: Boolean get() = muteAds && skipEnabled

    /**
     * True while this app is holding the media stream muted. Written to disk with commit()
     * so that if the process is killed mid-ad, the next start knows to unmute.
     */
    override var mutedByUs: Boolean
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

        private const val APP_ENABLED_PREFIX = "app_enabled_"
        private const val SKIPPED_PREFIX = "skipped_count_"

        fun appEnabledKey(app: WatchedApp) = APP_ENABLED_PREFIX + app.key
        private fun skippedKey(app: WatchedApp) = SKIPPED_PREFIX + app.key

        fun isAppEnabledKey(key: String?) = key != null && key.startsWith(APP_ENABLED_PREFIX)
        fun isStatsKey(key: String?) =
            key == KEY_SKIPPED || key == KEY_LAST_SKIP || (key != null && key.startsWith(SKIPPED_PREFIX))
    }
}
