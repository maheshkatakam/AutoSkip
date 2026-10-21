package io.github.autoskip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on your computer, no phone needed:  ./gradlew :app:testDebugUnitTest
 *
 * Each test builds a small fake screen and checks whether "Keep playing when asked" would
 * press anything, and what.
 */
class KeepPlayingDetectorTest {

    /**
     * An Android dialog in a window of its own: an optional title, a message, then buttons in
     * the order given. Returns the screen and each button's index by its label.
     */
    private fun dialog(title: String?, message: String, vararg buttons: String): Pair<TestScreen, Map<String, Int>> {
        val s = TestScreen()
        val decor = s.add(-1, box = Box(60, 900, 1020, 1500))
        val panel = s.add(decor, id = "parentPanel", box = Box(60, 900, 1020, 1500))
        if (title != null) {
            val top = s.add(panel, id = "topPanel", box = Box(60, 900, 1020, 1000))
            s.add(top, id = "alertTitle", text = title, box = Box(100, 920, 900, 980))
        }
        val content = s.add(panel, id = "contentPanel", box = Box(60, 1000, 1020, 1300))
        val scroll = s.add(content, id = "scrollView", box = Box(60, 1000, 1020, 1300))  // fits, so not scrollable
        s.add(scroll, id = "message", text = message, box = Box(100, 1020, 980, 1280))
        val bar = s.add(panel, id = "buttonPanel", box = Box(60, 1350, 1020, 1480))
        val ids = buttons.mapIndexed { i, label ->
            val left = 600 + i * 200
            label to s.add(bar, id = "button${i + 1}", text = label, clickable = true, box = Box(left - 400, 1360, left - 220, 1470))
        }.toMap()
        return s to ids
    }

    // ----- Wording -------------------------------------------------------------------------

    @Test
    fun fullPromptWording() {
        assertTrue(KeepPlayingDetector.isFullPrompt(SkipDetector.normalize("Video paused. Continue watching?")))
        assertTrue(KeepPlayingDetector.isFullPrompt(SkipDetector.normalize("Music paused. Continue listening?")))
        assertTrue(KeepPlayingDetector.isFullPrompt(SkipDetector.normalize("Are you still watching?")))
        assertTrue(KeepPlayingDetector.isFullPrompt(SkipDetector.normalize("Are you still listening?")))
        assertTrue(KeepPlayingDetector.isFullPrompt(SkipDetector.normalize("Are you still there?")))
        assertFalse(KeepPlayingDetector.isFullPrompt(SkipDetector.normalize("Continue watching")))
        assertFalse(KeepPlayingDetector.isFullPrompt(SkipDetector.normalize("Time to take a break?")))
        assertFalse(KeepPlayingDetector.isFullPrompt(SkipDetector.normalize("Video paused")))
    }

    @Test
    fun confirmLabels() {
        assertTrue(KeepPlayingDetector.isConfirmLabel(SkipDetector.normalize("YES")))
        assertTrue(KeepPlayingDetector.isConfirmLabel(SkipDetector.normalize("Continue")))
        assertTrue(KeepPlayingDetector.isConfirmLabel(SkipDetector.normalize("I'm still here")))
        assertFalse(KeepPlayingDetector.isConfirmLabel(SkipDetector.normalize("No")))
        assertFalse(KeepPlayingDetector.isConfirmLabel(SkipDetector.normalize("Yes, remove")))
    }

    // ----- What it presses -------------------------------------------------------------------

    @Test
    fun pressesYesOnYouTubesPrompt() {
        val (s, b) = dialog(null, "Video paused. Continue watching?", "Yes")
        val r = KeepPlayingDetector.find(s.nodes)
        assertNotNull(r)
        assertEquals(b["Yes"], r!!.target)
    }

    @Test
    fun pressesYesNotNoWhicheverComesFirst() {
        for (order in listOf(arrayOf("No", "Yes"), arrayOf("Yes", "No"))) {
            val (s, b) = dialog(null, "Video paused. Continue watching?", *order)
            assertEquals(b["Yes"], KeepPlayingDetector.find(s.nodes)?.target)
        }
    }

    @Test
    fun titleAndQuestionAsSeparateLabels() {
        val (s, b) = dialog("Music paused", "Continue listening?", "Not now", "Yes")
        assertEquals(b["Yes"], KeepPlayingDetector.find(s.nodes)?.target)
    }

    @Test
    fun stillListeningWithAContinueButton() {
        val (s, b) = dialog(null, "Are you still listening?", "Continue")
        assertEquals(b["Continue"], KeepPlayingDetector.find(s.nodes)?.target)
    }

    @Test
    fun pressesTheClickableRowAroundAYesLabel() {
        val s = TestScreen()
        val decor = s.add(-1, box = Box(60, 900, 1020, 1500))
        s.add(decor, text = "Are you still watching?", box = Box(100, 950, 980, 1050))
        val row = s.add(decor, clickable = true, box = Box(600, 1350, 980, 1470))
        s.add(row, text = "YES", box = Box(700, 1380, 880, 1440))
        assertEquals(row, KeepPlayingDetector.find(s.nodes)?.target)
    }

    @Test
    fun fullPromptAcceptsOk() {
        val (s, b) = dialog(null, "Video paused. Continue watching?", "OK")
        assertEquals(b["OK"], KeepPlayingDetector.find(s.nodes)?.target)
    }

    // ----- What it must never press ----------------------------------------------------------

    @Test
    fun ignoresYesInAnyOtherDialog() {
        val (s, _) = dialog(null, "Remove from Watch later?", "No", "Yes")
        assertNull(KeepPlayingDetector.find(s.nodes))
        val (s2, _) = dialog("Delete playlist?", "This can't be undone.", "Cancel", "Yes")
        assertNull(KeepPlayingDetector.find(s2.nodes))
    }

    @Test
    fun ignoresTheTakeABreakReminder() {
        val (s, _) = dialog("Time to take a break?", "You've been watching for 1 hour.", "Settings", "Dismiss")
        assertNull(KeepPlayingDetector.find(s.nodes))
        val (s2, _) = dialog("Time to take a break?", "You set a reminder.", "OK")
        assertNull(KeepPlayingDetector.find(s2.nodes))
    }

    @Test
    fun shortQuestionAloneNeedsAPlainYes() {
        // "Continue watching" is also a home-tab shelf; on its own it only counts with "Yes".
        val (s, _) = dialog(null, "Continue watching?", "Continue")
        assertNull(KeepPlayingDetector.find(s.nodes))
        val (s2, _) = dialog(null, "Continue watching?", "OK")
        assertNull(KeepPlayingDetector.find(s2.nodes))
        val (s3, b3) = dialog(null, "Continue watching?", "Yes")
        assertEquals(b3["Yes"], KeepPlayingDetector.find(s3.nodes)?.target)
    }

    @Test
    fun ignoresTheContinueWatchingShelfAndMiniplayer() {
        val s = TestScreen()
        val root = s.add(-1, box = Box(0, 0, 1080, 2400))
        val feed = s.add(root, id = "results", scrollable = true, box = Box(0, 200, 1080, 2200))
        s.add(feed, text = "Continue watching", box = Box(40, 220, 600, 280))
        for (i in 1..3) {
            val card = s.add(feed, clickable = true, box = Box(40, 300 + i * 400, 1040, 680 + i * 400))
            s.add(card, text = "Video $i", box = Box(60, 620 + i * 400, 900, 660 + i * 400))
        }
        val mini = s.add(root, id = "miniplayer", clickable = true, box = Box(0, 2200, 1080, 2400))
        s.add(mini, text = "Continue watching", box = Box(200, 2220, 800, 2280))
        assertNull(KeepPlayingDetector.find(s.nodes))
    }

    @Test
    fun neverVotesInACommunityPostPoll() {
        val s = TestScreen()
        val root = s.add(-1, box = Box(0, 0, 1080, 2400))
        val feed = s.add(root, id = "results", scrollable = true, box = Box(0, 200, 1080, 2200))
        val post = s.add(feed, box = Box(40, 300, 1040, 1100))
        s.add(post, text = "Some Channel", box = Box(60, 300, 600, 320))
        s.add(post, text = "2 days ago", box = Box(620, 300, 900, 320))
        s.add(post, text = "Are you still watching?", box = Box(60, 320, 1000, 400))
        val yes = s.add(post, clickable = true, box = Box(60, 500, 1000, 600))
        s.add(yes, text = "Yes", box = Box(80, 520, 300, 580))
        val no = s.add(post, clickable = true, box = Box(60, 620, 1000, 720))
        s.add(no, text = "No", box = Box(80, 640, 300, 700))
        s.add(post, desc = "Like", clickable = true, box = Box(60, 1000, 160, 1080))
        s.add(post, desc = "Comments", clickable = true, box = Box(200, 1000, 300, 1080))
        assertNull(KeepPlayingDetector.find(s.nodes))
    }

    @Test
    fun pollWordingNeverVouchesForAnotherButton() {
        // The "Continue watching" shelf's button sits next to a feed holding a poll that asks
        // "Are you still watching?". The poll's wording must not make "Continue" pressable.
        val s = TestScreen()
        val root = s.add(-1, box = Box(0, 0, 1080, 2400))
        val page = s.add(root, box = Box(0, 0, 1080, 2400))
        s.add(page, text = "Continue watching", box = Box(40, 200, 600, 260))
        s.add(page, text = "Continue", clickable = true, box = Box(40, 280, 300, 360))
        val feed = s.add(page, id = "results", scrollable = true, box = Box(0, 400, 1080, 2400))
        s.add(feed, text = "Are you still watching?", box = Box(60, 420, 1000, 500))
        s.add(feed, text = "Yes", clickable = true, box = Box(60, 520, 300, 600))
        s.add(feed, text = "No", clickable = true, box = Box(60, 620, 300, 700))
        for (i in 1..8) s.add(feed, text = "Video $i", box = Box(60, 700 + i * 100, 1000, 780 + i * 100))
        assertNull(KeepPlayingDetector.find(s.nodes))
    }

    @Test
    fun aLongPromptThatScrollsIsStillAnswered() {
        // A dialog's message box scrolls if the text doesn't fit (big font, small screen).
        val s = TestScreen()
        val decor = s.add(-1, box = Box(60, 900, 1020, 1500))
        val scroll = s.add(decor, id = "scrollView", scrollable = true, box = Box(60, 950, 1020, 1300))
        s.add(scroll, id = "message", text = "Video paused. Continue watching?", box = Box(100, 950, 980, 1600))
        val bar = s.add(decor, id = "buttonPanel", scrollable = true, box = Box(60, 1350, 1020, 1480))
        val yes = s.add(bar, id = "button1", text = "Yes", clickable = true, box = Box(700, 1360, 900, 1470))
        assertEquals(yes, KeepPlayingDetector.find(s.nodes)?.target)
    }

    @Test
    fun ignoresPromptWordingFarFromTheButton() {
        // A big screen (not a dialog) with a video titled like the prompt and a "Yes" elsewhere.
        val s = TestScreen()
        val root = s.add(-1, box = Box(0, 0, 1080, 2400))
        s.add(root, text = "Are you still watching? (official video)", box = Box(40, 700, 1040, 760))
        val other = s.add(root, box = Box(0, 800, 1080, 2400))
        for (i in 1..70) s.add(other, text = "Comment $i", box = Box(40, 800 + i * 20, 1040, 820 + i * 20))
        val yesRow = s.add(other, clickable = true, box = Box(40, 2300, 300, 2380))
        s.add(yesRow, text = "Yes", box = Box(60, 2310, 200, 2370))
        assertNull(KeepPlayingDetector.find(s.nodes))
    }

    @Test
    fun neverPressesAClickableDialogThatHoldsThePrompt() {
        // If the only clickable thing around "Yes" is the whole dialog, pressing it could do anything.
        val s = TestScreen()
        val decor = s.add(-1, clickable = true, box = Box(60, 900, 1020, 1500))
        s.add(decor, text = "Video paused. Continue watching?", box = Box(100, 950, 980, 1050))
        s.add(decor, text = "Yes", box = Box(700, 1380, 880, 1440))
        assertNull(KeepPlayingDetector.find(s.nodes))
    }

    @Test
    fun neverPressesADisabledOrHiddenButton() {
        val s = TestScreen()
        val decor = s.add(-1, box = Box(60, 900, 1020, 1500))
        s.add(decor, text = "Video paused. Continue watching?", box = Box(100, 950, 980, 1050))
        s.add(decor, text = "Yes", clickable = true, enabled = false, box = Box(700, 1380, 880, 1440))
        assertNull(KeepPlayingDetector.find(s.nodes))

        val s2 = TestScreen()
        val d2 = s2.add(-1, box = Box(60, 900, 1020, 1500))
        s2.add(d2, text = "Video paused. Continue watching?", box = Box(100, 950, 980, 1050))
        s2.add(d2, text = "Yes", clickable = true, visible = false, box = Box(700, 1380, 880, 1440))
        assertNull(KeepPlayingDetector.find(s2.nodes))
    }

    @Test
    fun ignoresAHiddenPrompt() {
        val s = TestScreen()
        val decor = s.add(-1, box = Box(60, 900, 1020, 1500))
        s.add(decor, text = "Video paused. Continue watching?", visible = false, box = Box(100, 950, 980, 1050))
        s.add(decor, text = "Yes", clickable = true, box = Box(700, 1380, 880, 1440))
        assertNull(KeepPlayingDetector.find(s.nodes))
    }

    @Test
    fun neverPressesAButtonWithAnAdvertiserLinkInside() {
        val s = TestScreen()
        val decor = s.add(-1, box = Box(60, 900, 1020, 1500))
        s.add(decor, text = "Are you still watching?", box = Box(100, 950, 980, 1050))
        val row = s.add(decor, clickable = true, box = Box(100, 1350, 980, 1470))
        s.add(row, text = "Yes", box = Box(120, 1380, 300, 1440))
        s.add(row, text = "Visit advertiser", box = Box(400, 1380, 900, 1440))
        assertNull(KeepPlayingDetector.find(s.nodes))
    }

    @Test
    fun promptWordingIsReportedForTheDebugLog() {
        val s = TestScreen()
        val decor = s.add(-1, box = Box(60, 900, 1020, 1500))
        val msg = s.add(decor, text = "Video paused. Continue watching?", box = Box(100, 950, 980, 1050))
        s.add(decor, text = "Sure thing", clickable = true, box = Box(700, 1380, 880, 1440))
        assertNull(KeepPlayingDetector.find(s.nodes))
        assertEquals(msg, KeepPlayingDetector.promptWording(s.nodes))
    }
}
