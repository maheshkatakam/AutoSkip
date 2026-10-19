package io.github.autoskip

/** The phone's media volume, as far as muting goes. [StreamMusicMute] is the real one. */
interface MediaMute {
    fun isMuted(): Boolean

    /** Returns false if Android refused. */
    fun setMuted(muted: Boolean): Boolean
}

/** What the muter needs to know and remember. [Prefs] is the real one. */
interface MuteMemory {
    /** "Mute ads while they play" is on, and skipping is switched on. */
    val muteEnabled: Boolean

    /** True while AutoSkip is holding the phone muted. Kept on disk so a restart can undo it. */
    var mutedByUs: Boolean
}

/**
 * Mutes the phone's media volume while an ad is on screen and puts it back afterwards.
 *
 * It uses a real mute, not "volume to zero", so Android keeps your volume level and
 * unmuting restores exactly what you had.
 *
 * Unmuting happens as early as possible, because since 2026 YouTube turns captions on
 * whenever the phone is muted (its "Show when muted" setting, on by default). A video that
 * starts while the phone is still muted therefore starts with captions on. So AutoSkip
 * unmutes the moment it presses Skip, and the moment no ad is on screen, rather than
 * waiting for the ad to be confirmed over.
 *
 * Every way out unmutes: Skip pressed, the ad leaving the screen, the app closing, the
 * setting or the service being switched off, a 3-minute ceiling on any single mute, and a
 * restart (see [MuteMemory.mutedByUs]). If you press a volume key during an ad, it leaves
 * that ad alone.
 *
 * No Android code here, so every rule is unit-tested (AdMuterTest).
 */
class AdMuter(
    private val media: MediaMute,
    private val memory: MuteMemory,
    private val log: (String) -> Unit = {},
) {
    private var mutedAt = NONE
    private var skipPressedAt = NONE

    /** You unmuted this ad yourself (or the ceiling hit). Cleared when the ad is over. */
    private var leftAlone = false

    /**
     * Called after every scan. [adOnScreen]: this scan saw an ad. [adActive]: an ad (or a run
     * of back-to-back ads) is still considered under way, which outlasts a single scan.
     */
    fun update(adOnScreen: Boolean, adActive: Boolean, now: Long) {
        if (!memory.muteEnabled) {
            release("setting off")
            return
        }
        if (!adActive) {
            leftAlone = false
            skipPressedAt = NONE
            release("ad over")
            return
        }
        if (!adOnScreen) {
            // The ad's controls are gone, so the video may be starting: give it its sound now.
            release("no ad on screen")
            return
        }
        if (skipPressedAt != NONE && now - skipPressedAt < REMUTE_AFTER_SKIP_MS) {
            // Just pressed Skip. The ad's labels can linger a moment; don't mute the video.
            return
        }
        if (memory.mutedByUs) {
            if (!media.isMuted()) {
                // You pressed a volume key during the ad. Respect that for the rest of it.
                memory.mutedByUs = false
                mutedAt = NONE
                leftAlone = true
                log("volume changed by you; leaving this ad alone")
            } else if (mutedAt != NONE && now - mutedAt > MAX_MUTE_MS) {
                release("ceiling reached")
                leftAlone = true
            }
            return
        }
        if (leftAlone || media.isMuted()) return // you chose to hear it, or it's already silent
        if (media.setMuted(true)) {
            memory.mutedByUs = true
            mutedAt = now
            log("muted for ad")
        }
    }

    /** Skip was just pressed: unmute now, before the video starts. */
    fun onSkipPressed(now: Long) {
        skipPressedAt = now
        release("skip pressed")
    }

    /** Unmute if (and only if) AutoSkip was the one that muted. Safe to call any time. */
    fun release(reason: String) {
        if (!memory.mutedByUs) return
        if (media.isMuted()) media.setMuted(false)
        memory.mutedByUs = false
        mutedAt = NONE
        log("unmuted ($reason)")
    }

    companion object {
        const val MAX_MUTE_MS = 3 * 60 * 1000L

        /** After pressing Skip, wait this long before muting again (the next ad in a run). */
        const val REMUTE_AFTER_SKIP_MS = 1500L

        private const val NONE = Long.MIN_VALUE
    }
}
