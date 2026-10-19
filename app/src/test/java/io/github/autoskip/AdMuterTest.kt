package io.github.autoskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plays scan-by-scan timelines through AdMuter with a fake phone volume, the way
 * AutoSkipService calls it. Runs on your computer:  ./gradlew testDebugUnitTest
 */
class AdMuterTest {

    private class FakePhone : MediaMute {
        var muted = false
        var refuses = false
        var changes = 0
        override fun isMuted() = muted
        override fun setMuted(muted: Boolean): Boolean {
            if (refuses) return false
            if (this.muted != muted) changes++
            this.muted = muted
            return true
        }
    }

    private class FakeMemory(override var muteEnabled: Boolean = true) : MuteMemory {
        override var mutedByUs = false
    }

    private class Sim(enabled: Boolean = true, private val step: Long = 300) {
        val phone = FakePhone()
        val memory = FakeMemory(enabled)
        val muter = AdMuter(phone, memory)
        var now = 1_000L

        /** [scans] scans; [ad] = an ad is on screen; [active] = the ad is still under way. */
        fun scans(n: Int, ad: Boolean, active: Boolean = ad) = repeat(n) {
            muter.update(ad, active, now)
            now += step
        }

        fun pressSkip() = muter.onSkipPressed(now)
    }

    @Test
    fun mutesWhileAnAdIsOnScreen() {
        val s = Sim()
        s.scans(1, ad = true)
        assertTrue(s.phone.muted)
        assertTrue(s.memory.mutedByUs)
    }

    @Test
    fun skipPressUnmutesAtOnceSoTheVideoStartsWithSound() {
        // Why captions came on: the video started while the phone was still muted, and
        // YouTube turns captions on for muted playback.
        val s = Sim()
        s.scans(10, ad = true)          // countdown, muted
        assertTrue(s.phone.muted)
        s.pressSkip()
        assertFalse("must be unmuted the moment Skip is pressed", s.phone.muted)
        s.scans(2, ad = true)           // the ad's labels linger for a moment...
        assertFalse("...but it must not mute the video", s.phone.muted)
        s.scans(5, ad = false, active = true)
        assertFalse(s.phone.muted)
        s.scans(5, ad = false, active = false)
        assertFalse(s.phone.muted)
        assertEquals(2, s.phone.changes) // one mute, one unmute: no flapping
    }

    @Test
    fun unmutesOnTheFirstScanWithoutAnAd() {
        // An ad that simply ends (no Skip button): unmute on the first scan where it's gone,
        // not after the service's 1.2 s "is it really over?" wait.
        val s = Sim()
        s.scans(10, ad = true)
        s.scans(1, ad = false, active = true)
        assertFalse(s.phone.muted)
    }

    @Test
    fun theNextAdInARunIsMutedToo() {
        val s = Sim()
        s.scans(5, ad = true)
        s.pressSkip()
        s.scans(2, ad = true)           // still inside the 1.5 s after the press
        assertFalse(s.phone.muted)
        s.scans(5, ad = true)           // ad 2 of 2 is playing
        assertTrue(s.phone.muted)
    }

    @Test
    fun aVolumeKeyLeavesTheAdAlone() {
        val s = Sim()
        s.scans(3, ad = true)
        s.phone.muted = false           // you pressed volume up
        s.scans(10, ad = true)
        assertFalse(s.phone.muted)
        assertFalse(s.memory.mutedByUs)
        s.scans(5, ad = false, active = false)
        s.scans(1, ad = true)           // a later ad is muted again
        assertTrue(s.phone.muted)
    }

    @Test
    fun neverUnmutesAPhoneYouMutedYourself() {
        val s = Sim()
        s.phone.muted = true            // muted before the ad started
        s.scans(5, ad = true)
        s.pressSkip()
        s.scans(5, ad = false, active = false)
        assertTrue(s.phone.muted)
        assertEquals(0, s.phone.changes)
    }

    @Test
    fun releasesAfterThreeMinutesAtMost() {
        val s = Sim(step = 10_000)
        s.scans(1, ad = true)
        assertTrue(s.phone.muted)
        s.scans(20, ad = true)          // 200 s of "ad"
        assertFalse(s.phone.muted)
    }

    @Test
    fun turningTheSettingOffUnmutes() {
        val s = Sim()
        s.scans(3, ad = true)
        s.memory.muteEnabled = false
        s.scans(1, ad = true)
        assertFalse(s.phone.muted)
    }

    @Test
    fun doesNothingWhenTheSettingIsOff() {
        val s = Sim(enabled = false)
        s.scans(10, ad = true)
        assertFalse(s.phone.muted)
        assertEquals(0, s.phone.changes)
    }

    @Test
    fun aRestartUndoesAMuteLeftBehind() {
        val s = Sim()
        s.phone.muted = true
        s.memory.mutedByUs = true       // the process died mid-ad
        AdMuter(s.phone, s.memory).release("service started")
        assertFalse(s.phone.muted)
        assertFalse(s.memory.mutedByUs)
    }

    @Test
    fun aRefusedMuteIsNotRemembered() {
        val s = Sim()
        s.phone.refuses = true
        s.scans(3, ad = true)
        assertFalse(s.memory.mutedByUs)
    }
}
