package io.github.autoskip

/**
 * Finds the prompt YouTube and YouTube Music show after a long stretch without a tap, which
 * pauses playback ("Video paused. Continue watching?"), and the button that answers yes.
 * Pressing it is the "Keep playing when asked" setting, which is off unless you turn it on.
 *
 * Seen on a real phone (October 2026): YouTube Music shows the same wording as YouTube,
 * "Video paused. Continue watching?", with a blue "Yes". The other wordings below are
 * variants YouTube has used or may use, kept so a rewording doesn't break this.
 *
 * The rule: only press a yes-type button ("Yes", "Continue"...) that sits in the same small
 * dialog as wording that can only be this prompt. Like [SkipDetector], it has no Android code,
 * so every rule is covered by unit tests (KeepPlayingDetectorTest).
 *
 * What it must never press: a "Yes" in any other dialog ("Remove from Watch later?"), a
 * "Yes" in a community-post poll, the "Continue watching" shelf on the home tab or the
 * miniplayer's resume bar (both use those words with no prompt), YouTube's "Time to take a
 * break?" reminder, which you set up yourself, or any "No", "Cancel" or advertiser button.
 *
 * Maintenance: YouTube can reword the prompt. With Debug logging on, AutoSkip logs the
 * screen once when it sees prompt-like wording but no button it trusts (see README, "When
 * YouTube changes its UI"). Add the new wording below, with a test.
 */
object KeepPlayingDetector {

    data class Result(
        /** Index of the node to press. */
        val target: Int,
        /** Index of the node carrying the yes-type label (often [target] itself). */
        val label: Int,
        /** Index of a node of the prompt's wording, for logs. */
        val prompt: Int,
    )

    // ----- The prompt's wording (all matched after SkipDetector.normalize) ----------------

    /**
     * Wording that can only be this prompt, wherever it appears in a label:
     * "Video paused. Continue watching?", "Music paused. Continue listening?",
     * "Are you still watching?", "Are you still listening?", "Are you still there?".
     */
    private val FULL_PROMPTS = listOf(
        Regex("(^| )(video|music|song|playback|audio) paused (continue|keep) (watching|listening|playing)( |$)"),
        Regex("(^| )are you still (watching|listening|there)( |$)"),
    )

    /** A title such as "Video paused", when the question is a separate label. */
    private val PAUSED_TITLE = Regex("^(video|music|song|playback|audio|your (video|music)) (is )?paused$")

    /**
     * Short questions that count only as a label of their own. "Continue watching" is also
     * the name of a home-tab shelf, and "Still there" could be a song title, so on their own
     * (without a "paused" title next to them) they need a plain "Yes" button in the same dialog.
     */
    private val QUESTIONS = setOf(
        "still watching", "still listening", "still there",
        "continue watching", "continue listening", "continue playing",
        "keep watching", "keep listening", "keep playing",
        "do you want to continue watching", "do you want to continue listening",
        "do you want to keep watching", "do you want to keep listening",
    )

    // ----- The button that answers yes ------------------------------------------------------

    private const val YES = "yes"

    private val CONFIRM_LABELS = setOf(
        YES, "continue", "continue watching", "continue listening", "continue playing",
        "keep watching", "keep listening", "keep playing", "resume", "ok",
        "i m still here", "i m still watching", "i m still listening", "i m here", "still here",
    )

    /** If the button we'd press contains any of these, it isn't the yes button. */
    private val REFUSE_LABELS = setOf(
        "no", "cancel", "stop", "exit", "close", "dismiss", "not now", "settings", "sign out",
        "remove", "delete", "report", "block",
    )

    /** How far up from the button we look for the dialog around it. */
    private const val MAX_DIALOG_HOPS = 6

    /** A dialog is small. Any container bigger than this is a whole screen, not a dialog. */
    private const val MAX_DIALOG_NODES = 60

    /** How far up from a label we look for the pressable button around it. */
    private const val MAX_BUTTON_HOPS = 3

    /** A button is a handful of nodes at most (a label, maybe an icon). */
    private const val MAX_BUTTON_NODES = 8

    /** A scrolling container this big is a list (a feed), not part of a dialog. */
    private const val SCROLLING_LIST_MIN_NODES = 9

    private enum class Strength { NONE, WEAK, STRONG }

    // ---------------------------------------------------------------------------------

    fun isConfirmLabel(label: String): Boolean = label in CONFIRM_LABELS

    /** True for the prompt's full wording, e.g. "video paused continue watching". */
    fun isFullPrompt(label: String): Boolean = label.isNotEmpty() && FULL_PROMPTS.any { it.containsMatchIn(label) }

    /**
     * The button to press, if the prompt is on screen with a yes-type button in the same
     * dialog. Null otherwise.
     */
    fun find(nodes: List<UiNode>): Result? {
        for (n in nodes) {
            if (!n.visible) continue
            val label = when {
                isConfirmLabel(n.text) -> n.text
                isConfirmLabel(n.desc) -> n.desc
                else -> continue
            }
            val target = buttonTarget(nodes, n) ?: continue
            if (insideScrollingList(nodes, target)) continue
            val prompt = promptAround(nodes, target, label) ?: continue
            return Result(target.index, n.index, prompt)
        }
        return null
    }

    /**
     * True if [node] sits in a list that scrolls, such as the home feed or a channel's posts.
     * The prompt is a dialog on top of the player, never an item in a list, while a community
     * post can be a poll asking "Are you still watching?" with "Yes" and "No" options. Pressing
     * that would vote for you, and its wording must not vouch for any other button either.
     *
     * A dialog can scroll too (a long message, or stacked buttons on a small screen), but
     * that scrolling part holds a few nodes, while a list holds many.
     */
    private fun insideScrollingList(nodes: List<UiNode>, node: UiNode): Boolean {
        var p = node.parent
        while (p >= 0) {
            val a = nodes[p]
            if (a.scrollable && a.subtreeEnd - a.index + 1 >= SCROLLING_LIST_MIN_NODES) return true
            p = a.parent
        }
        return false
    }

    /**
     * A node showing the prompt's full wording, whether or not a button was found: for the
     * debug log, so a reworded button can be spotted after a YouTube update.
     */
    fun promptWording(nodes: List<UiNode>): Int? =
        nodes.firstOrNull { it.visible && (isFullPrompt(it.text) || isFullPrompt(it.desc)) }?.index

    /** From a yes-type label up to the pressable button around it, if it is one we trust. */
    private fun buttonTarget(nodes: List<UiNode>, from: UiNode): UiNode? {
        var node: UiNode? = from
        var hops = 0
        while (node != null && hops <= MAX_BUTTON_HOPS) {
            if (node.clickable && node.enabled && node.visible && !node.bounds.isEmpty()) {
                if (node.subtreeEnd - node.index + 1 > MAX_BUTTON_NODES) return null
                for (i in node.index..node.subtreeEnd) {
                    val t = nodes[i]
                    for (s in arrayOf(t.text, t.desc)) {
                        if (s in REFUSE_LABELS || SkipDetector.isNeverPress(s) || isFullPrompt(s)) return null
                    }
                }
                return node
            }
            node = if (node.parent >= 0) nodes[node.parent] else null
            hops++
        }
        return null
    }

    /**
     * Looks for the prompt's wording in the small dialog around [button]: the nearest
     * ancestors, stopping once a container is too big to be a dialog. Returns the index of
     * a node of the wording, or null.
     */
    private fun promptAround(nodes: List<UiNode>, button: UiNode, confirmLabel: String): Int? {
        var container = if (button.parent >= 0) nodes[button.parent] else return null
        var hops = 0
        while (hops < MAX_DIALOG_HOPS) {
            if (container.subtreeEnd - container.index + 1 > MAX_DIALOG_NODES) return null
            val (strength, at) = promptIn(nodes, container, button)
            when (strength) {
                Strength.STRONG -> return at
                Strength.WEAK -> return if (confirmLabel == YES) at else null
                Strength.NONE -> {}
            }
            if (container.parent < 0) return null
            container = nodes[container.parent]
            hops++
        }
        return null
    }

    /** The prompt's wording inside [container], ignoring the button itself. */
    private fun promptIn(nodes: List<UiNode>, container: UiNode, button: UiNode): Pair<Strength, Int> {
        var pausedTitle = -1
        var question = -1
        for (i in container.index..container.subtreeEnd) {
            if (i in button.index..button.subtreeEnd) continue
            val n = nodes[i]
            if (!n.visible) continue
            if (n.text.isEmpty() && n.desc.isEmpty()) continue
            if (insideScrollingList(nodes, n)) continue
            for (s in arrayOf(n.text, n.desc)) {
                if (s.isEmpty()) continue
                if (isFullPrompt(s)) return Strength.STRONG to i
                if (pausedTitle < 0 && PAUSED_TITLE.matches(s)) pausedTitle = i
                if (question < 0 && s in QUESTIONS) question = i
            }
        }
        return when {
            pausedTitle >= 0 && question >= 0 -> Strength.STRONG to question
            question >= 0 -> Strength.WEAK to question
            else -> Strength.NONE to -1
        }
    }
}
