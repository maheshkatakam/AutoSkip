package io.github.autoskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on your computer, no phone needed:  ./gradlew testDebugUnitTest
 *
 * Each test builds a small fake YouTube screen and checks what the detector decides.
 */
class SkipDetectorTest {

    private val window = Box(0, 0, 1080, 2400)
    private val player = Box(0, 0, 1080, 608)
    private val skipBox = Box(800, 480, 1060, 560)

    /** Builds a pre-order node list. Add a node's children before its next sibling. */
    private class Screen {
        val nodes = ArrayList<UiNode>()
        fun add(
            parent: Int,
            id: String? = null,
            text: String = "",
            desc: String = "",
            clickable: Boolean = false,
            enabled: Boolean = true,
            visible: Boolean = true,
            box: Box = Box(0, 0, 100, 50),
        ): Int {
            val n = UiNode(
                nodes.size, parent, id, SkipDetector.normalize(text), SkipDetector.normalize(desc),
                clickable, enabled, visible, box,
            )
            nodes += n
            var p = parent
            while (p >= 0) {
                nodes[p].subtreeEnd = n.index
                p = nodes[p].parent
            }
            return n.index
        }
    }

    private fun screenWithPlayer(): Pair<Screen, Int> {
        val s = Screen()
        val root = s.add(-1, box = window)
        val playerNode = s.add(root, id = "player_view", clickable = true, box = player)
        return s to playerNode
    }

    // ----- text rules ----------------------------------------------------------------

    @Test
    fun normalizeFoldsCaseAndPunctuation() {
        assertEquals("skip ad", SkipDetector.normalize("SKIP AD  ›"))
        assertEquals("ad 0 15", SkipDetector.normalize("Ad · 0:15"))
        assertEquals("", SkipDetector.normalize(null))
    }

    @Test
    fun entryNameStripsPackage() {
        assertEquals("skip_ad_button", SkipDetector.entryName("com.google.android.youtube:id/skip_ad_button"))
        assertNull(SkipDetector.entryName(""))
    }

    @Test
    fun countdownsAreRecognised() {
        assertTrue(SkipDetector.isCountdown("skip ad in 5"))
        assertTrue(SkipDetector.isCountdown("skip in 3"))
        assertTrue(SkipDetector.isCountdown("5"))
        assertFalse(SkipDetector.isCountdown("skip ad"))
        assertFalse(SkipDetector.isCountdown("skip ads"))
    }

    @Test
    fun strongSkipLabels() {
        assertTrue(SkipDetector.isStrongSkipLabel("skip ad"))
        assertTrue(SkipDetector.isStrongSkipLabel("skip ads"))
        assertTrue(SkipDetector.isStrongSkipLabel("skip advert"))
        assertFalse(SkipDetector.isStrongSkipLabel("skip ad in 5"))
        assertFalse(SkipDetector.isStrongSkipLabel("skip adidas haul"))
        assertFalse(SkipDetector.isStrongSkipLabel("skip"))
    }

    @Test
    fun adLabels() {
        assertTrue(SkipDetector.isAdLabel("ad 1 of 2"))
        assertTrue(SkipDetector.isAdLabel("ad 0 15"))
        assertTrue(SkipDetector.isAdLabel("sponsored 0 16"))
        assertTrue(SkipDetector.isAdLabel("ad will end in 5"))
        assertTrue(SkipDetector.isAdLabel("skip ad in 5"))
        assertFalse(SkipDetector.isAdLabel("sponsored"))      // feed cards say this with no ad playing
        assertFalse(SkipDetector.isAdLabel("ad astra trailer"))
        assertFalse(SkipDetector.isAdLabel("ad"))
    }

    // ----- whole-screen decisions ----------------------------------------------------

    @Test
    fun pressesSkipButtonFoundByViewId() {
        val (s, p) = screenWithPlayer()
        s.add(p, id = "ad_progress_text", text = "Sponsored · 0:16", box = Box(20, 540, 300, 580))
        val button = s.add(p, id = "skip_ad_button", clickable = true, box = skipBox)
        s.add(button, text = "Skip", box = Box(820, 495, 1000, 545))

        val r = SkipDetector.analyze(s.nodes, window)
        assertTrue(r.adOnScreen)
        assertEquals(button, r.target)
        assertEquals(SkipDetector.Match.VIEW_ID, r.match)
    }

    @Test
    fun pressesClickableParentOfSkipAdLabel() {
        val (s, p) = screenWithPlayer()
        val button = s.add(p, clickable = true, box = skipBox)
        val label = s.add(button, text = "Skip ad", box = Box(820, 495, 1000, 545))

        val r = SkipDetector.analyze(s.nodes, window)
        assertEquals(button, r.target)
        assertEquals(label, r.label)
    }

    @Test
    fun waitsDuringCountdown() {
        val (s, p) = screenWithPlayer()
        val button = s.add(p, clickable = true, enabled = false, box = skipBox)
        s.add(button, text = "Skip ad in 5", box = Box(820, 495, 1000, 545))

        val r = SkipDetector.analyze(s.nodes, window)
        assertTrue(r.adOnScreen)
        assertTrue(r.waiting)
        assertNull(r.target)
    }

    @Test
    fun doesNotPressWhileCountdownSitsInsideButton() {
        val (s, p) = screenWithPlayer()
        s.add(p, id = "ad_progress_text", text = "Ad · 0:12", box = Box(20, 540, 300, 580))
        val button = s.add(p, clickable = true, box = skipBox)
        s.add(button, text = "Skip", box = Box(820, 495, 950, 545))
        s.add(button, text = "4", box = Box(960, 495, 1000, 545))

        val r = SkipDetector.analyze(s.nodes, window)
        assertNull(r.target)
    }

    @Test
    fun genericSkipNeedsAnAdOnScreen() {
        val (s, p) = screenWithPlayer()
        // An ordinary video whose title is just "Skip". Nothing says an ad is playing.
        s.add(p, text = "Skip", clickable = true, box = Box(20, 650, 600, 700))

        val r = SkipDetector.analyze(s.nodes, window)
        assertFalse(r.adOnScreen)
        assertNull(r.target)
    }

    @Test
    fun genericSkipPressedWhenAdIsPlaying() {
        val (s, p) = screenWithPlayer()
        s.add(p, id = "ad_progress_text", text = "Sponsored · 0:09", box = Box(20, 540, 300, 580))
        val button = s.add(p, clickable = true, box = skipBox)
        s.add(button, desc = "Skip", box = Box(820, 495, 1000, 545))

        val r = SkipDetector.analyze(s.nodes, window)
        assertEquals(button, r.target)
        assertEquals(SkipDetector.Match.GENERIC_LABEL, r.match)
    }

    @Test
    fun feedSponsoredCardIsNotAnAd() {
        val s = Screen()
        val root = s.add(-1, box = window)
        val card = s.add(root, clickable = true, box = Box(0, 700, 1080, 1300))
        s.add(card, text = "Sponsored", box = Box(20, 1200, 200, 1240))
        s.add(card, text = "Shop now", clickable = true, box = Box(800, 1200, 1060, 1260))

        val r = SkipDetector.analyze(s.nodes, window)
        assertFalse(r.adOnScreen)
        assertNull(r.target)
    }

    @Test
    fun neverPressesAContainerHoldingAnAdvertiserLink() {
        val (s, p) = screenWithPlayer()
        val overlay = s.add(p, clickable = true, box = Box(780, 400, 1070, 600))
        s.add(overlay, text = "Visit advertiser", box = Box(790, 410, 1060, 460))
        s.add(overlay, text = "Skip ad", box = Box(820, 495, 1000, 545))

        val r = SkipDetector.analyze(s.nodes, window)
        assertTrue(r.adOnScreen)
        assertNull(r.target)
    }

    @Test
    fun neverPressesAFullScreenContainer() {
        val s = Screen()
        val root = s.add(-1, clickable = true, box = window)
        val wrapper = s.add(root, box = Box(800, 480, 1060, 560))
        s.add(wrapper, text = "Skip ad", box = Box(820, 495, 1000, 545))

        val r = SkipDetector.analyze(s.nodes, window)
        assertNull(r.target)
    }

    @Test
    fun ignoresInvisibleSkipButton() {
        val (s, p) = screenWithPlayer()
        s.add(p, id = "skip_ad_button", clickable = true, visible = false, box = skipBox)

        val r = SkipDetector.analyze(s.nodes, window)
        assertFalse(r.adOnScreen)
        assertNull(r.target)
    }

    @Test
    fun detectsCardDrawnOverTheButton() {
        val (s, p) = screenWithPlayer()
        val button = s.add(p, id = "skip_ad_button", clickable = true, box = skipBox)
        s.add(p, text = "Install", clickable = true, box = Box(0, 400, 1080, 608)) // later = on top

        val target = s.nodes[button]
        assertTrue(SkipDetector.isPointCovered(s.nodes, target, skipBox.centerX, skipBox.centerY))
        assertFalse(SkipDetector.isPointCovered(s.nodes, target, 100, 100))
    }
}
