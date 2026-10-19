package io.github.autoskip

/**
 * Decides when to press Skip and when a press counts as a skipped ad.
 *
 * The service calls it once per scan of the screen, with the scan's time in milliseconds.
 * It has no Android code, so every rule below is covered by unit tests (PressTrackerTest).
 *
 * The rules:
 *  - Press once the Skip control has been ready for the delay you chose.
 *  - After a press, wait [COOLDOWN_MS] before pressing again (the app may still be reacting),
 *    and give up after [MAX_ATTEMPTS] presses on the same button.
 *  - A press counts as a skip once the button has stayed gone for [CONFIRM_MS]. A button that
 *    vanishes for a moment and comes back (the screen redrawing) is not a skip, and a button
 *    that comes back means the press didn't work, so it is pressed again and still counts once.
 *  - If the ad ends (or AutoSkip stops watching) while a press is waiting to be confirmed,
 *    it counts then.
 */
class PressTracker {

    private var readySince = NONE
    private var cooldownUntil = NONE
    private var attempts = 0
    private var pressedIn: WatchedApp? = null
    private var absentSince = NONE

    /** True while a press is waiting for its button to go away. */
    val pressPending: Boolean get() = pressedIn != null

    /**
     * A pressable Skip control is on screen. Returns true if it should be pressed now.
     * When it returns true, report the outcome with [onPressed].
     */
    fun onTargetVisible(now: Long, delayMs: Long): Boolean {
        absentSince = NONE
        if (now < cooldownUntil || attempts >= MAX_ATTEMPTS) return false
        if (readySince == NONE) readySince = now
        return now - readySince >= delayMs
    }

    /** Skip was pressed in [app]. [delivered] is false if Android refused every way of pressing it. */
    fun onPressed(app: WatchedApp, now: Long, delivered: Boolean) {
        attempts++
        cooldownUntil = now + COOLDOWN_MS
        readySince = NONE
        if (delivered) pressedIn = app
    }

    /**
     * No pressable Skip control is on screen. Returns the app to credit with a skipped ad,
     * once the button has been gone for [CONFIRM_MS] after a press; otherwise null.
     */
    fun onTargetAbsent(now: Long): WatchedApp? {
        val app = pressedIn
        if (app == null) {
            readySince = NONE
            attempts = 0
            return null
        }
        if (absentSince == NONE) absentSince = now
        if (now - absentSince < CONFIRM_MS) return null
        reset()
        return app
    }

    /** The ad is over, or AutoSkip stopped watching. Returns the app to credit, if a press was pending. */
    fun onAdEnded(): WatchedApp? {
        val app = pressedIn
        reset()
        return app
    }

    private fun reset() {
        readySince = NONE
        cooldownUntil = NONE
        attempts = 0
        pressedIn = null
        absentSince = NONE
    }

    companion object {
        const val COOLDOWN_MS = 1500L
        const val CONFIRM_MS = 600L
        const val MAX_ATTEMPTS = 4
        private const val NONE = Long.MIN_VALUE
    }
}
