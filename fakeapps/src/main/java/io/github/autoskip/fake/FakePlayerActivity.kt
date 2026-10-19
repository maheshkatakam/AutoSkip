package io.github.autoskip.fake

import android.app.Activity
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

/**
 * A fake player for the emulator test. It plays [ads] skippable ads, then one unskippable
 * ad that ends on its own, then a stretch of normal playback, and records (tag AutoSkipTest,
 * and files/events.log) everything that happens:
 *
 *   AD_SKIPPED n latency=…ms   Skip pressed after it became pressable (good)
 *   EARLY_PRESS n              Skip pressed during its countdown (bad)
 *   AD_NOT_SKIPPED n           nobody pressed Skip within 8 s (bad)
 *   WRONG_PRESS what           a trap was pressed: the advertiser link, a Premium upsell,
 *                              or a plain "Skip" button shown with no ad playing (bad)
 *   AD_MUTED / AD_NOT_MUTED n  whether the phone was muted while the ad played
 *   VIDEO_STARTED_MUTED n      the phone was still muted 0.3 s after Skip (bad when muting:
 *                              the real YouTube turns captions on for muted playback)
 *   RESULT skipped=… expected=… wrong=… early=… missed=… muted_ads=… late_unmute=…
 *          unskippable_muted=… unskippable_late=… muted_normal=… token=…
 *
 * The "youtube" flavour mimics YouTube: view IDs (ad_progress_text, skip_ad_button) and a
 * countdown written inside the Skip button. The "music" flavour mimics what YouTube Music
 * may show instead: no view IDs at all, "Your music will resume after this ad", a separate
 * countdown, then a "Skip ad" button. Together they exercise both of AutoSkip's ways of
 * finding the button.
 */
class FakePlayerActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val musicStyle get() = packageName.endsWith(".music")
    private val audio by lazy { getSystemService(AudioManager::class.java) }

    private var adsTotal = 3
    private var token = ""   // set by the test for each round, echoed in START and RESULT
    private var adIndex = 0
    private var skipped = 0
    private var wrong = 0
    private var early = 0
    private var missed = 0
    private var mutedAds = 0
    private var lateUnmute = 0
    private var unskippableMuted = 0
    private var unskippableLate = 0
    private var mutedNormal = 0
    private var skipReady = false
    private var readyAt = 0L
    private var inAd = false
    private var finished = false

    private lateinit var status: TextView
    private lateinit var adBox: LinearLayout
    private lateinit var adLabel: TextView
    private lateinit var countdown: TextView
    private lateinit var skip: Button
    private lateinit var normalBox: LinearLayout

    private val timeout = Runnable {
        missed++
        log("AD_NOT_SKIPPED $adIndex")
        endAd(skippedNow = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        adsTotal = intent.getIntExtra("ads", 3)
        token = intent.getStringExtra("token") ?: "none"
        eventsFile.delete()
        setContentView(buildUi())
        log("START token=$token app=$packageName ads=$adsTotal style=${if (musicStyle) "music" else "youtube"}")
        handler.postDelayed({ startAd() }, START_DELAY_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun phoneMuted(): Boolean = audio?.isStreamMute(AudioManager.STREAM_MUSIC) == true

    // --- Skippable ads -------------------------------------------------------------------

    private fun startAd() {
        adIndex++
        inAd = true
        skipReady = false
        status.text = "Ad $adIndex of $adsTotal"
        normalBox.visibility = View.GONE
        adBox.visibility = View.VISIBLE
        adLabel.text = if (musicStyle) "Your music will resume after this ad" else "Ad · 0:15"
        if (musicStyle) {
            skip.visibility = View.GONE
            countdown.visibility = View.VISIBLE
        } else {
            skip.visibility = View.VISIBLE
            countdown.visibility = View.GONE
        }
        tick(COUNTDOWN_SECONDS)
    }

    private fun tick(left: Int) {
        if (!inAd) return
        if (left > 0) {
            if (musicStyle) countdown.text = "Skip in $left" else skip.text = "Skip ad in $left"
            handler.postDelayed({ tick(left - 1) }, 1000)
            return
        }
        // The ad has been playing for 3 s: if AutoSkip mutes ads, the phone is muted by now.
        if (phoneMuted()) {
            mutedAds++
            log("AD_MUTED $adIndex")
        } else {
            log("AD_NOT_MUTED $adIndex")
        }
        skipReady = true
        readyAt = SystemClock.uptimeMillis()
        if (musicStyle) {
            countdown.visibility = View.GONE
            skip.text = "Skip ad"
            skip.visibility = View.VISIBLE
        } else {
            skip.text = "Skip"
        }
        handler.postDelayed(timeout, SKIP_TIMEOUT_MS)
    }

    private fun onSkipPressed() {
        if (!inAd) {
            wrong++
            log("WRONG_PRESS skip-outside-ad")
            return
        }
        if (!skipReady) {
            early++
            log("EARLY_PRESS $adIndex")
            return
        }
        skipped++
        log("AD_SKIPPED $adIndex latency=${SystemClock.uptimeMillis() - readyAt}ms")
        endAd(skippedNow = true)
    }

    private fun endAd(skippedNow: Boolean) {
        handler.removeCallbacks(timeout)
        inAd = false
        skipReady = false
        adBox.visibility = View.GONE
        status.text = "Video playing"
        val ad = adIndex
        if (skippedNow) {
            // The video is playing again. The real YouTube decides on captions about now.
            handler.postDelayed({
                if (phoneMuted()) {
                    lateUnmute++
                    log("VIDEO_STARTED_MUTED $ad")
                } else {
                    log("VIDEO_UNMUTED $ad")
                }
            }, UNMUTE_AFTER_SKIP_MS)
        }
        handler.postDelayed({
            if (adIndex < adsTotal) startAd() else startUnskippableAd()
        }, VIDEO_MS)
    }

    // --- An ad with no Skip button, which simply ends (like many YouTube Music audio ads) --

    private fun startUnskippableAd() {
        inAd = true
        status.text = "Unskippable ad"
        adBox.visibility = View.VISIBLE
        adLabel.text = if (musicStyle) "Your music will resume after this ad" else "Ad · 0:04"
        skip.visibility = View.GONE
        countdown.visibility = View.GONE
        handler.postDelayed({
            if (phoneMuted()) unskippableMuted = 1
            log(if (unskippableMuted == 1) "UNSKIPPABLE_MUTED" else "UNSKIPPABLE_NOT_MUTED")
        }, UNSKIPPABLE_MS / 2)
        handler.postDelayed({ endUnskippableAd() }, UNSKIPPABLE_MS)
    }

    private fun endUnskippableAd() {
        inAd = false
        adBox.visibility = View.GONE
        status.text = "Video playing"
        handler.postDelayed({
            if (phoneMuted()) unskippableLate = 1
            log(if (unskippableLate == 1) "UNSKIPPABLE_VIDEO_STARTED_MUTED" else "UNSKIPPABLE_VIDEO_UNMUTED")
        }, UNMUTE_AFTER_AD_END_MS)
        handler.postDelayed({ startNormalPlayback() }, VIDEO_MS)
    }

    // --- Normal playback -------------------------------------------------------------------

    /** No ad on screen, but a song titled "Skip Ads (Remix)" and a plain Skip button. Nothing may be pressed. */
    private fun startNormalPlayback() {
        status.text = "Now playing"
        normalBox.visibility = View.VISIBLE
        handler.postDelayed({ if (phoneMuted()) mutedNormal++ }, 1000)
        handler.postDelayed({ finishTest() }, NORMAL_MS)
    }

    private fun finishTest() {
        if (finished) return
        finished = true
        if (phoneMuted()) mutedNormal++
        normalBox.visibility = View.GONE
        status.text = "Done"
        log(
            "RESULT skipped=$skipped expected=$adsTotal wrong=$wrong early=$early missed=$missed " +
                "muted_ads=$mutedAds late_unmute=$lateUnmute unskippable_muted=$unskippableMuted " +
                "unskippable_late=$unskippableLate muted_normal=$mutedNormal token=$token",
        )
    }

    // ---------------------------------------------------------------------------------

    private fun buildUi(): View {
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        fun lp() = LinearLayout.LayoutParams(wrap, wrap).apply { topMargin = 24 }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 160, 48, 48)
        }
        status = TextView(this).apply { text = "Starting"; textSize = 20f }
        root.addView(status, lp())

        adBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        adLabel = TextView(this).apply {
            if (!musicStyle) id = R.id.ad_progress_text
        }
        adBox.addView(adLabel, lp())
        countdown = TextView(this).apply { visibility = View.GONE }
        adBox.addView(countdown, lp())
        skip = Button(this).apply {
            if (!musicStyle) id = R.id.skip_ad_button
            isAllCaps = false
            setOnClickListener { onSkipPressed() }
        }
        adBox.addView(skip, lp())
        // Traps shown during every ad. Pressing either opens the ad or a purchase screen.
        adBox.addView(trap("Visit advertiser", "advertiser"), lp())
        adBox.addView(trap("Skip ads with Premium", "premium-upsell"), lp())
        root.addView(adBox, LinearLayout.LayoutParams(match, wrap))

        normalBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        normalBox.addView(TextView(this).apply { text = "Skip Ads (Remix)"; textSize = 18f }, lp())
        normalBox.addView(trap("Skip", "skip-with-no-ad"), lp())
        root.addView(normalBox, LinearLayout.LayoutParams(match, wrap))
        return root
    }

    private fun trap(label: String, what: String) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener {
            wrong++
            log("WRONG_PRESS $what")
        }
    }

    /** Every event goes to logcat and to files/events.log, which the test reads with run-as. */
    private val eventsFile get() = File(filesDir, "events.log")

    private fun log(msg: String) {
        Log.i(TAG, msg)
        try {
            eventsFile.appendText("$msg\n")
        } catch (e: Exception) {
            Log.w(TAG, "could not write events.log", e)
        }
    }

    companion object {
        private const val TAG = "AutoSkipTest"
        private const val START_DELAY_MS = 1500L
        private const val COUNTDOWN_SECONDS = 3
        private const val SKIP_TIMEOUT_MS = 8000L
        private const val VIDEO_MS = 3000L
        private const val UNSKIPPABLE_MS = 4000L
        private const val NORMAL_MS = 5000L

        /** The phone must be unmuted this soon after Skip is pressed... */
        private const val UNMUTE_AFTER_SKIP_MS = 300L

        /** ...and this soon after an unskippable ad ends (AutoSkip has to notice it's gone). */
        private const val UNMUTE_AFTER_AD_END_MS = 700L
    }
}
