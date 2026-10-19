package io.github.autoskip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches the YouTube and YouTube Music apps and presses their "Skip" button once it appears.
 *
 * How it works, in the same spirit as the YouTube Ad Auto-skipper browser extension:
 *  1. Android delivers this service events from those two apps only (see
 *     res/xml/autoskip_service.xml). Each event schedules a scan of the app's screen.
 *  2. A scan copies the app's accessibility tree into a flat list and asks
 *     [SkipDetector] whether an ad is on screen and whether the Skip control is ready.
 *  3. While an ad is on screen it rescans every 300 ms (the extension polls every
 *     200 ms); while one of the apps is open with no ad it rescans once a second as a
 *     safety net; with neither on screen it does nothing at all.
 *  4. When the control is ready (and any delay you chose has passed) it presses it with
 *     ACTION_CLICK, the same action TalkBack uses. Only if the app refuses that does it
 *     fall back to a tap at the button, and never if something is drawn over the button.
 *
 * It has no INTERNET permission, so nothing it reads can leave the phone.
 */
class AutoSkipService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var muter: AdMuter

    private val handler = Handler(Looper.getMainLooper())
    private val scanRunnable = Runnable { scan() }
    private var scheduledFor = Long.MAX_VALUE
    private var lastScanAt = 0L

    // State for the ad (or run of back-to-back ads) currently on screen.
    private var adActive = false
    private var adStartedAt = 0L
    private var adLastSeenAt = 0L
    private val presses = PressTracker()   // when to press, and when a press counts as a skip
    private var dumpedThisAd = false

    private val rect = Rect()

    private class AppWindow(val root: AccessibilityNodeInfo, val box: Box, val layer: Int, val app: WatchedApp)
    private class AnyWindow(val box: Box, val layer: Int, val isWatchedApp: Boolean)

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        muter = AdMuter(this, prefs)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // If the process was killed while an ad had us muted, put the sound back first.
        muter.release("service started")
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        isRunning = true
        log("connected")
        requestScan(0)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val app = WatchedApp.of(event?.packageName) ?: return
        if (!prefs.isAppEnabled(app)) return
        requestScan(MIN_SCAN_GAP_MS)
    }

    override fun onInterrupt() {
        muter.release("interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == Prefs.KEY_ENABLED || key == Prefs.KEY_MUTE || Prefs.isAppEnabledKey(key)) {
            muter.update(adActive, SystemClock.uptimeMillis())
            requestScan(0)
        }
    }

    private fun shutdown() {
        isRunning = false
        handler.removeCallbacks(scanRunnable)
        scheduledFor = Long.MAX_VALUE
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        muter.release("service stopped")
    }

    // ---------------------------------------------------------------------------------
    // Scheduling

    /** Scan no sooner than [gapMs] after the previous scan. An earlier request wins. */
    private fun requestScan(gapMs: Long) {
        val now = SystemClock.uptimeMillis()
        val at = maxOf(now, lastScanAt + gapMs)
        if (at >= scheduledFor) return
        handler.removeCallbacks(scanRunnable)
        scheduledFor = at
        handler.postAtTime(scanRunnable, at)
    }

    // ---------------------------------------------------------------------------------
    // Scanning

    private fun scan() {
        scheduledFor = Long.MAX_VALUE
        val now = SystemClock.uptimeMillis()
        lastScanAt = now

        if (!prefs.skipEnabled) {
            endAd(now)
            return
        }

        val allWindows = ArrayList<AnyWindow>()
        val appWindows = try {
            findAppWindows(allWindows)
        } catch (e: RuntimeException) {
            Log.w(TAG, "could not read windows", e)
            emptyList()
        }
        if (appWindows.isEmpty()) {
            // Neither app is on screen (or both are switched off). Their next event wakes us.
            endAd(now)
            return
        }

        var adSeen = false
        var waiting = false
        var targetSeen = false
        for (w in appWindows) {
            val nodes = ArrayList<UiNode>(512)
            val handles = ArrayList<AccessibilityNodeInfo>(512)
            try {
                collect(w.root, -1, 0, nodes, handles)
            } catch (e: RuntimeException) {
                // The tree changed under us mid-read. The next scan will see the new one.
                continue
            }
            if (nodes.isEmpty()) continue

            val r = SkipDetector.analyze(nodes, w.box)
            if (r.adOnScreen) adSeen = true
            if (r.waiting) waiting = true
            if (r.target != null && r.label != null) {
                if (!targetSeen) tryPress(now, nodes, handles, r, w, allWindows)
                targetSeen = true
            } else if (r.adOnScreen && !r.waiting) {
                maybeDump(now, w.app, nodes, r)
            }
        }

        if (!targetSeen) {
            // The control is gone. If we pressed it and it stays gone, that's a skipped ad.
            presses.onTargetAbsent(now)?.let { credit(it, "button gone") }
        }

        if (adSeen) {
            adLastSeenAt = now
            if (!adActive) {
                adActive = true
                adStartedAt = now
                dumpedThisAd = false
                log("ad on screen")
            }
        } else if (adActive && now - adLastSeenAt > AD_GONE_MS) {
            endAd(now)
        }
        muter.update(adActive, now)

        val busy = adActive || waiting || targetSeen
        requestScan(if (busy) FAST_POLL_MS else IDLE_POLL_MS)
    }

    private fun endAd(now: Long) {
        if (adActive) log("ad gone")
        adActive = false
        presses.onAdEnded()?.let { credit(it, "ad ended") }
        muter.update(false, now)
    }

    private fun credit(app: WatchedApp, why: String) {
        prefs.recordSkip(app, System.currentTimeMillis())
        log("skip counted in ${app.key} ($why); total ${prefs.skippedCount}")
    }

    /** The on-screen windows of the watched apps that are switched on, plus every window's position. */
    private fun findAppWindows(all: MutableList<AnyWindow>): List<AppWindow> {
        val result = ArrayList<AppWindow>(2)
        for (w in windows) {
            w.getBoundsInScreen(rect)
            val box = Box(rect.left, rect.top, rect.right, rect.bottom)
            val root = w.root
            val app = WatchedApp.of(root?.packageName)
            all += AnyWindow(box, w.layer, app != null)
            if (root != null && app != null && prefs.isAppEnabled(app)) result += AppWindow(root, box, w.layer, app)
        }
        if (result.isEmpty()) {
            // Some devices report no window list; fall back to the focused window.
            val root = rootInActiveWindow
            val app = WatchedApp.of(root?.packageName)
            if (root != null && app != null && prefs.isAppEnabled(app)) {
                root.getBoundsInScreen(rect)
                result += AppWindow(root, Box(rect.left, rect.top, rect.right, rect.bottom), Int.MAX_VALUE, app)
            }
        }
        return result
    }

    /** Copies the tree into a flat pre-order list, keeping the live nodes alongside for clicking. */
    private fun collect(
        node: AccessibilityNodeInfo,
        parent: Int,
        depth: Int,
        out: MutableList<UiNode>,
        handles: MutableList<AccessibilityNodeInfo>,
    ) {
        if (out.size >= MAX_NODES || depth > MAX_DEPTH) return
        node.getBoundsInScreen(rect)
        val ui = UiNode(
            index = out.size,
            parent = parent,
            viewId = SkipDetector.entryName(node.viewIdResourceName),
            text = SkipDetector.normalize(node.text),
            desc = SkipDetector.normalize(node.contentDescription),
            clickable = node.isClickable,
            enabled = node.isEnabled,
            visible = node.isVisibleToUser,
            bounds = Box(rect.left, rect.top, rect.right, rect.bottom),
        )
        out += ui
        handles += node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, ui.index, depth + 1, out, handles)
        }
        ui.subtreeEnd = out.size - 1
    }

    // ---------------------------------------------------------------------------------
    // Pressing

    private fun tryPress(
        now: Long,
        nodes: List<UiNode>,
        handles: List<AccessibilityNodeInfo>,
        r: SkipDetector.Result,
        window: AppWindow,
        allWindows: List<AnyWindow>,
    ) {
        if (!presses.onTargetVisible(now, prefs.delaySeconds * 1000L)) return

        val target = nodes[r.target!!]
        val label = nodes[r.label!!]
        var how: String? = null

        if (handles[target.index].performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            how = "ACTION_CLICK"
        } else if (label.index != target.index &&
            handles[label.index].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        ) {
            how = "ACTION_CLICK on label"
        } else {
            val x = label.bounds.centerX
            val y = label.bounds.centerY
            val covered = SkipDetector.isPointCovered(nodes, target, x, y) ||
                isCoveredByAnotherWindow(x, y, window, allWindows)
            if (covered) {
                log("not tapping: something is drawn over the Skip button")
            } else if (tap(x, y)) {
                how = "tap at $x,$y"
            }
        }

        presses.onPressed(window.app, now, delivered = how != null)
        if (how != null) log("pressed Skip in ${window.app.key} via $how (${r.match})")
    }

    private fun isCoveredByAnotherWindow(x: Int, y: Int, mine: AppWindow, all: List<AnyWindow>): Boolean =
        all.any { !it.isWatchedApp && it.layer > mine.layer && it.box.contains(x, y) }

    private fun tap(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0) return false
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // ---------------------------------------------------------------------------------
    // Logging

    private fun log(msg: String) {
        if (prefs.verboseLogging) Log.d(TAG, msg)
    }

    /**
     * With verbose logging on, if an ad has been on screen for a while and no Skip control
     * was recognised, write every labelled node to logcat once. That's the material you need
     * to update SkipDetector after a YouTube or YouTube Music update.
     */
    private fun maybeDump(now: Long, app: WatchedApp, nodes: List<UiNode>, r: SkipDetector.Result) {
        if (!prefs.verboseLogging || dumpedThisAd || !adActive || now - adStartedAt < DUMP_AFTER_MS) return
        dumpedThisAd = true
        Log.d(TAG, "${app.key}: ad on screen (${r.adEvidence}) but no Skip control recognised. Labelled nodes:")
        for (n in nodes) {
            if (n.viewId == null && n.text.isEmpty() && n.desc.isEmpty()) continue
            Log.d(
                TAG,
                "  #${n.index} id=${n.viewId} text='${n.text}' desc='${n.desc}' " +
                    "click=${n.clickable} enabled=${n.enabled} visible=${n.visible} ${n.bounds}",
            )
        }
    }

    companion object {
        private const val TAG = "AutoSkip"

        /** Read by the app screen to tell "enabled" apart from "actually running". */
        @Volatile
        var isRunning = false
            private set

        private const val MIN_SCAN_GAP_MS = 250L
        private const val FAST_POLL_MS = 300L
        private const val IDLE_POLL_MS = 1000L
        private const val AD_GONE_MS = 1200L
        private const val TAP_DURATION_MS = 60L
        private const val DUMP_AFTER_MS = 6000L
        private const val MAX_NODES = 3000
        private const val MAX_DEPTH = 64
    }
}
