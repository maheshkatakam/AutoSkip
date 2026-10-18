package io.github.autoskip

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs

    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var openSettings: Button
    private lateinit var restrictedHelp: View
    private lateinit var youtubeMissing: View
    private lateinit var stats: TextView
    private lateinit var delayLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        val root = findViewById<View>(R.id.root)
        applySystemBarPadding(root)

        statusTitle = findViewById(R.id.status_title)
        statusDetail = findViewById(R.id.status_detail)
        openSettings = findViewById(R.id.open_accessibility_settings)
        restrictedHelp = findViewById(R.id.restricted_help)
        youtubeMissing = findViewById(R.id.youtube_missing)
        stats = findViewById(R.id.stats)
        delayLabel = findViewById(R.id.delay_label)

        openSettings.setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.open_app_info).setOnClickListener { openAppInfo() }
        findViewById<Button>(R.id.reset_stats).setOnClickListener { prefs.resetStats() }

        bindSwitch(R.id.switch_enabled, prefs.skipEnabled) { prefs.skipEnabled = it }
        bindSwitch(R.id.switch_mute, prefs.muteAds) { prefs.muteAds = it }
        bindSwitch(R.id.switch_verbose, prefs.verboseLogging) { prefs.verboseLogging = it }

        val delay = findViewById<SeekBar>(R.id.delay_seekbar)
        delay.max = Prefs.MAX_DELAY_SECONDS
        delay.progress = prefs.delaySeconds
        updateDelayLabel(prefs.delaySeconds)
        delay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) prefs.delaySeconds = value
                updateDelayLabel(value)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        // Restricted settings only exist on Android 13 and later.
        restrictedHelp.visibility = if (Build.VERSION.SDK_INT >= 33) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        refresh()
    }

    override fun onPause() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key == Prefs.KEY_SKIPPED || key == Prefs.KEY_LAST_SKIP || key == null) refresh()
    }

    private fun refresh() {
        val inSettings = isEnabledInSettings()
        val running = isRunningNow()
        when {
            running -> {
                statusTitle.setText(R.string.status_on)
                statusDetail.setText(R.string.status_on_detail)
                openSettings.setText(R.string.button_manage)
            }
            inSettings -> {
                statusTitle.setText(R.string.status_stuck)
                statusDetail.setText(R.string.status_stuck_detail)
                openSettings.setText(R.string.button_open_settings)
            }
            else -> {
                statusTitle.setText(R.string.status_off)
                statusDetail.setText(R.string.status_off_detail)
                openSettings.setText(R.string.button_open_settings)
            }
        }
        youtubeMissing.visibility = if (isYouTubeInstalled()) View.GONE else View.VISIBLE

        val count = prefs.skippedCount
        val last = prefs.lastSkipAt
        stats.text = if (count == 0 || last == 0L) {
            getString(R.string.stats_none)
        } else {
            val ago = DateUtils.getRelativeTimeSpanString(
                last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            )
            resources.getQuantityString(R.plurals.stats_count, count, count, ago)
        }
    }

    private fun updateDelayLabel(seconds: Int) {
        delayLabel.text = if (seconds == 0) {
            getString(R.string.delay_none)
        } else {
            resources.getQuantityString(R.plurals.delay_seconds, seconds, seconds)
        }
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        val s = findViewById<Switch>(id)
        s.isChecked = initial
        s.setOnCheckedChangeListener { _, checked -> onChange(checked) }
    }

    // ---------------------------------------------------------------------------------

    private val serviceComponent: ComponentName
        get() = ComponentName(this, AutoSkipService::class.java)

    /** Your choice in Settings. Survives the process dying, so it can be on while nothing runs. */
    private fun isEnabledInSettings(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val full = serviceComponent.flattenToString()
        val short = serviceComponent.flattenToShortString()
        return enabled.split(':').any { it.equals(full, true) || it.equals(short, true) }
    }

    /** What Android has actually bound right now. */
    private fun isRunningNow(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java) ?: return AutoSkipService.isRunning
        val bound = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val si = it.resolveInfo?.serviceInfo
                si != null && si.packageName == packageName && si.name == AutoSkipService::class.java.name
            }
        return bound || AutoSkipService.isRunning
    }

    private fun isYouTubeInstalled(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(AutoSkipService.YOUTUBE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(AutoSkipService.YOUTUBE, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun openAccessibilitySettings() {
        start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppInfo() {
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_settings_screen, Toast.LENGTH_LONG).show()
        }
    }

    /** Apps targeting Android 15+ draw edge to edge; keep content clear of the status and navigation bars. */
    private fun applySystemBarPadding(root: View) {
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = if (Build.VERSION.SDK_INT >= 30) {
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                intArrayOf(i.left, i.top, i.right, i.bottom)
            } else {
                legacyInsets(insets)
            }
            v.setPadding(baseLeft + bars[0], baseTop + bars[1], baseRight + bars[2], baseBottom + bars[3])
            insets
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyInsets(insets: WindowInsets): IntArray = intArrayOf(
        insets.systemWindowInsetLeft,
        insets.systemWindowInsetTop,
        insets.systemWindowInsetRight,
        insets.systemWindowInsetBottom,
    )
}
