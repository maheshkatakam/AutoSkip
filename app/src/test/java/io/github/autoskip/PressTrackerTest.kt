package io.github.autoskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plays scan-by-scan timelines through PressTracker, the way AutoSkipService calls it,
 * and counts the skips it credits. Runs on your computer:  ./gradlew :app:testDebugUnitTest
 */
class PressTrackerTest {

    /** Drives a tracker like the service does: one call per scan, every [step] ms. */
    private class Sim(private val delayMs: Long = 0, private val step: Long = 300) {
        val tracker = PressTracker()
        var now = 1_000L
        var presses = 0
        val credited = mutableListOf<WatchedApp>()
        /** Whether a press "works" (the app accepts it). */
        var pressesDeliver = true

        /** [scans] scans with the Skip button on screen in [app]. */
        fun visible(app: WatchedApp = WatchedApp.YOUTUBE, scans: Int = 1) = repeat(scans) {
            if (tracker.onTargetVisible(now, delayMs)) {
                presses++
                tracker.onPressed(app, now, pressesDeliver)
            }
            now += step
        }

        /** [scans] scans with no Skip button on screen. */
        fun absent(scans: Int = 1) = repeat(scans) {
            tracker.onTargetAbsent(now)?.let { credited += it }
            now += step
        }

        fun adEnded() {
            tracker.onAdEnded()?.let { credited += it }
        }
    }

    @Test
    fun skipThatWorksAtOnceIsCounted() {
        // The bug in 1.0: the button disappears ~300 ms after the press, well inside the
        // 1.5 s press cooldown, and the pending skip was thrown away instead of counted.
        val s = Sim()
        s.visible()          // pressed
        s.absent(5)          // gone at once, and stays gone
        assertEquals(1, s.presses)
        assertEquals(listOf(WatchedApp.YOUTUBE), s.credited)
    }

    @Test
    fun countedOnlyAfterTheButtonStaysGone() {
        val s = Sim(step = 100)
        s.visible()
        s.absent(5)                                   // 500 ms: not yet
        assertTrue(s.credited.isEmpty())
        s.absent(2)                                   // past 600 ms
        assertEquals(1, s.credited.size)
    }

    @Test
    fun threeAdsInARowCountThree() {
        val s = Sim()
        repeat(3) {
            s.absent(10)     // countdown / video playing
            s.visible()      // Skip appears and is pressed
            s.absent(10)     // ad gone, video plays
        }
        assertEquals(3, s.presses)
        assertEquals(3, s.credited.size)
    }

    @Test
    fun aMomentaryFlickerIsNotASkip() {
        val s = Sim(step = 100)
        s.visible()          // pressed
        s.absent(2)          // gone for 200 ms...
        s.visible(scans = 1) // ...back: the screen was just redrawing, press didn't work yet
        assertTrue(s.credited.isEmpty())
        s.absent(10)         // now really gone
        assertEquals(listOf(WatchedApp.YOUTUBE), s.credited)
    }

    @Test
    fun aPressThatDidNotWorkIsRetriedAndCountedOnce() {
        val s = Sim()
        s.visible(scans = 8) // 2.4 s on screen: pressed, cooldown, pressed again
        assertEquals(2, s.presses)
        s.absent(5)
        assertEquals(1, s.credited.size)
    }

    @Test
    fun givesUpAfterFourPresses() {
        val s = Sim()
        s.visible(scans = 60) // 18 s of a button that never goes away
        assertEquals(PressTracker.MAX_ATTEMPTS, s.presses)
    }

    @Test
    fun aRefusedPressIsNeverCounted() {
        val s = Sim()
        s.pressesDeliver = false
        s.visible()
        s.absent(10)
        assertEquals(1, s.presses)
        assertTrue(s.credited.isEmpty())
    }

    @Test
    fun noPressNoCount() {
        val s = Sim()
        s.absent(20)
        s.adEnded()
        assertTrue(s.credited.isEmpty())
    }

    @Test
    fun delayIsRespected() {
        val s = Sim(delayMs = 3000)
        s.visible(scans = 10) // 0 .. 2.7 s
        assertEquals(0, s.presses)
        s.visible(scans = 1)  // 3.0 s
        assertEquals(1, s.presses)
    }

    @Test
    fun adEndingBeforeConfirmationStillCountsOnce() {
        val s = Sim(step = 100)
        s.visible()
        s.absent(2)
        s.adEnded()          // e.g. you left the app right after the skip
        s.absent(10)
        s.adEnded()
        assertEquals(1, s.credited.size)
    }

    @Test
    fun creditedToTheAppItHappenedIn() {
        val s = Sim()
        s.visible(WatchedApp.YOUTUBE_MUSIC)
        s.absent(5)
        s.visible(WatchedApp.YOUTUBE)
        s.absent(5)
        assertEquals(listOf(WatchedApp.YOUTUBE_MUSIC, WatchedApp.YOUTUBE), s.credited)
    }

    @Test
    fun nextAdGetsFreshAttempts() {
        val s = Sim()
        s.pressesDeliver = false
        s.visible(scans = 60)                 // first ad: four refused presses
        s.absent(5)
        s.pressesDeliver = true
        s.visible()                           // next ad's button
        assertEquals(PressTracker.MAX_ATTEMPTS + 1, s.presses)
        s.absent(5)
        assertEquals(1, s.credited.size)
        assertFalse(s.tracker.pressPending)
        assertNull(s.tracker.onAdEnded())
    }
}
