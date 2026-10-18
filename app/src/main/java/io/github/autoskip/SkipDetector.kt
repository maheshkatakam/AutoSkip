package io.github.autoskip

import java.util.Locale

/**
 * Screen rectangle, in screen pixels. A tiny stand-in for android.graphics.Rect so
 * this file has no Android dependencies and can be unit-tested on a plain JVM.
 */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    fun isEmpty(): Boolean = width <= 0 || height <= 0
    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
}

/**
 * The parts of one accessibility node the detector looks at.
 *
 * Nodes are kept in a flat list in depth-first pre-order. That order matters twice:
 * a node's descendants are the indexes from [index] to [subtreeEnd], and nodes later
 * in the list are (roughly) drawn on top of earlier ones.
 */
data class UiNode(
    val index: Int,
    val parent: Int,          // -1 for the window's root
    val viewId: String?,      // entry name only, e.g. "skip_ad_button"
    val text: String,         // already passed through SkipDetector.normalize
    val desc: String,         // content description, normalised the same way
    val clickable: Boolean,
    val enabled: Boolean,
    val visible: Boolean,
    val bounds: Box,
    var subtreeEnd: Int = index,
)

/**
 * Decides, from a snapshot of the YouTube app's screen, whether an ad is playing and
 * whether YouTube's own Skip control is on screen and ready to press.
 *
 * The rule this whole app is built around: only ever press the control YouTube itself
 * offers, and only once YouTube has made it pressable. Nothing here blocks, hides or
 * shortens an ad any other way.
 *
 * Maintenance: YouTube renames IDs and relabels buttons between app versions, and
 * translates labels into the phone's language. When skipping stops working, dump the
 * live screen while an ad plays (see README, "When YouTube changes its UI") and add
 * what you find to the lists below.
 */
object SkipDetector {

    enum class Match { VIEW_ID, LABEL, GENERIC_LABEL }

    data class Result(
        /** Some evidence on screen that an ad is playing right now. */
        val adOnScreen: Boolean,
        /** What that evidence was, for logs. */
        val adEvidence: String?,
        /** Index of the node to press, if a Skip control is ready now. */
        val target: Int?,
        /** Index of the node that identified the control (often a label inside [target]). */
        val label: Int?,
        val match: Match?,
        /** A skip control or countdown is visible but not pressable yet. */
        val waiting: Boolean,
    )

    // ----- What the Skip control looks like ------------------------------------------

    /** Any view ID containing this is part of YouTube's skip control. Language-independent. */
    private const val SKIP_ID_FRAGMENT = "skip_ad"

    /** Words that may follow "skip" in an unambiguous label: "Skip ad", "Skip ads", "Skip advert". */
    private val AD_WORDS = setOf("ad", "ads", "advert", "adverts", "advertisement", "advertisements")

    /** A bare "Skip" label. Only trusted while other evidence says an ad is playing. */
    private const val GENERIC_SKIP = "skip"

    // ----- What tells us an ad is playing --------------------------------------------

    /** View IDs that only exist in the player while an ad is playing. */
    private val AD_VIEW_IDS = setOf("ad_progress_text", "player_learn_more_button")

    /** Labels that can only appear during an ad. Matched as a prefix of the normalised text. */
    private val AD_LABEL_PREFIXES = listOf(
        "ad will end",
        "advert will end",
        "video will play after ad",
        "video will resume after",
        "your video will begin",
    )

    /**
     * Badge words that count only in a counter shape such as "Ad 1 of 2", "Ad · 0:15"
     * or "Sponsored · 0:16": the badge word followed by two or more numbers. A bare
     * "Sponsored" is deliberately not enough, because promoted cards in the home feed
     * carry that label while no ad is playing.
     */
    private val BADGE_WORDS = setOf("ad", "ads", "sponsored", "advertisement")

    // ----- What we must never press --------------------------------------------------

    /**
     * Advertiser calls to action. If the node we are about to press contains one of
     * these, it is the ad itself (or a card around it), not the Skip control, and
     * pressing it would open the advertiser. We refuse.
     */
    private val CTA_PREFIXES = listOf(
        "visit", "learn more", "install", "shop", "download", "get app", "get the app",
        "open app", "sign up", "book now", "apply now", "order now", "buy now", "call now",
        "my ad center", "why this ad",
    )

    /** The Skip button is small. Refuse to press any container larger than this share of the window. */
    private const val MAX_TARGET_WIDTH_SHARE = 0.6
    private const val MAX_TARGET_HEIGHT_SHARE = 0.35

    /** How far up from a label we look for the pressable control around it. */
    private const val MAX_ANCESTOR_HOPS = 4

    private val COUNTDOWN = Regex("(^| )in \\d")

    // ---------------------------------------------------------------------------------

    /** Lowercase words separated by single spaces: "SKIP AD  ›" and "Skip ad" both become "skip ad". */
    fun normalize(raw: CharSequence?): String {
        if (raw.isNullOrEmpty()) return ""
        val lowered = raw.toString().lowercase(Locale.ROOT)
        val out = StringBuilder(lowered.length)
        var pendingSpace = false
        for (c in lowered) {
            if (c.isLetterOrDigit()) {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(c)
            } else {
                pendingSpace = true
            }
        }
        return out.toString()
    }

    /** "com.google.android.youtube:id/skip_ad_button" -> "skip_ad_button". */
    fun entryName(resourceName: String?): String? {
        if (resourceName.isNullOrEmpty()) return null
        val i = resourceName.indexOf(":id/")
        return (if (i >= 0) resourceName.substring(i + 4) else resourceName).lowercase(Locale.ROOT)
    }

    /** "Skip ad in 5", "Skip in 3", "Ad will end in 4", or a bare "5": not pressable yet. */
    fun isCountdown(label: String): Boolean =
        label.isNotEmpty() && (COUNTDOWN.containsMatchIn(label) || label.all { it.isDigit() || it == ' ' })

    /** "skip ad", "skip ads", "skip advert"... as the first two words. */
    fun isStrongSkipLabel(label: String): Boolean {
        if (isCountdown(label)) return false
        val words = label.split(' ')
        return words.size >= 2 && words[0] == "skip" && words[1] in AD_WORDS
    }

    fun isAdLabel(label: String): Boolean {
        if (label.isEmpty()) return false
        val words = label.split(' ')
        // "Skip ad", and the countdown before it ("Skip ad in 5"), both mean an ad is on screen.
        if (words.size >= 2 && words[0] == "skip" && words[1] in AD_WORDS) return true
        if (AD_LABEL_PREFIXES.any { label.startsWith(it) }) return true
        // Counter shapes: "ad 1 of 2", "ad 0 15", "sponsored 0 16".
        if (words.size >= 3 && words[0] in BADGE_WORDS) {
            val rest = words.drop(1)
            val numbers = rest.count { w -> w.all { it.isDigit() } }
            if (numbers >= 2 && rest.all { w -> w == "of" || w.all { it.isDigit() } }) return true
        }
        return false
    }

    private fun isCta(label: String): Boolean = label.isNotEmpty() && CTA_PREFIXES.any { label.startsWith(it) }

    private fun adSignal(n: UiNode): String? {
        if (!n.visible) return null
        val id = n.viewId
        if (id != null && (id in AD_VIEW_IDS || id.contains(SKIP_ID_FRAGMENT))) return "id:$id"
        if (isAdLabel(n.text)) return "text:${n.text}"
        if (isAdLabel(n.desc)) return "desc:${n.desc}"
        return null
    }

    private fun skipMatch(n: UiNode, adContext: Boolean): Match? {
        if (!n.visible) return null
        if (n.viewId?.contains(SKIP_ID_FRAGMENT) == true) return Match.VIEW_ID
        if (isStrongSkipLabel(n.text) || isStrongSkipLabel(n.desc)) return Match.LABEL
        if (adContext && (n.text == GENERIC_SKIP || n.desc == GENERIC_SKIP)) return Match.GENERIC_LABEL
        return null
    }

    private fun fitsButtonSize(b: Box, window: Box): Boolean {
        if (b.isEmpty()) return false
        if (window.isEmpty()) return true
        return b.width <= window.width * MAX_TARGET_WIDTH_SHARE &&
            b.height <= window.height * MAX_TARGET_HEIGHT_SHARE
    }

    private fun subtreeHas(nodes: List<UiNode>, root: UiNode, test: (String) -> Boolean): Boolean {
        for (i in root.index..root.subtreeEnd) {
            val n = nodes[i]
            if (test(n.text) || test(n.desc)) return true
        }
        return false
    }

    /**
     * Walks up from the node that identified the control to the nearest node that will
     * accept a press: visible, enabled, clickable, button-sized, and not wrapping an
     * advertiser call to action.
     */
    fun clickTarget(nodes: List<UiNode>, from: UiNode, window: Box): UiNode? {
        var node: UiNode? = from
        var hops = 0
        while (node != null && hops <= MAX_ANCESTOR_HOPS) {
            if (!fitsButtonSize(node.bounds, window)) return null
            if (node.clickable && node.enabled && node.visible) {
                return if (subtreeHas(nodes, node, ::isCta)) null else node
            }
            node = if (node.parent >= 0) nodes[node.parent] else null
            hops++
        }
        return null
    }

    fun analyze(nodes: List<UiNode>, window: Box): Result {
        var evidence: String? = null
        for (n in nodes) {
            evidence = adSignal(n)
            if (evidence != null) break
        }
        val adContext = evidence != null
        val waitingCountdown = nodes.any { it.visible && isAdLabel(it.text) && isCountdown(it.text) } ||
            nodes.any { it.visible && isAdLabel(it.desc) && isCountdown(it.desc) }

        val candidates = nodes.mapNotNull { n -> skipMatch(n, adContext)?.let { n to it } }
            .sortedBy { it.second.ordinal }

        for ((node, match) in candidates) {
            val target = clickTarget(nodes, node, window) ?: continue
            // A countdown inside the control means YouTube hasn't made it pressable yet.
            if (subtreeHas(nodes, target, ::isCountdown)) continue
            return Result(true, evidence ?: "skip:${match.name}", target.index, node.index, match, false)
        }

        val adOnScreen = adContext || candidates.isNotEmpty()
        return Result(adOnScreen, evidence, null, null, null, candidates.isNotEmpty() || waitingCountdown)
    }

    /**
     * Before falling back to a synthetic tap at a point, check nothing pressable is drawn
     * on top of that point. YouTube sometimes slides an install card over the Skip button;
     * tapping through it would press the ad, which this app must never do.
     */
    fun isPointCovered(nodes: List<UiNode>, target: UiNode, x: Int, y: Int): Boolean {
        for (i in target.subtreeEnd + 1 until nodes.size) {
            val n = nodes[i]
            if (n.visible && n.clickable && n.bounds.contains(x, y)) return true
        }
        return false
    }
}
