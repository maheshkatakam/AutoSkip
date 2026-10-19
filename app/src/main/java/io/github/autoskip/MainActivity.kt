package io.github.autoskip

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
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
    private lateinit var appsMissing: View
    private lateinit var stats: TextView
    private lateinit var statsBreakdown: TextView
    private lateinit var delayLabel: TextView
    private lateinit var appSwitches: Map<WatchedApp, Switch>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        applyTestSettings(intent)

        val root = findViewById<View>(R.id.root)
        applySystemBarPadding(root)

        statusTitle = findViewById(R.id.status_title)
        statusDetail = findViewById(R.id.status_detail)
        openSettings = findViewById(R.id.open_accessibility_settings)
        restrictedHelp = findViewById(R.id.restricted_help)
        appsMissing = findViewById(R.id.apps_missing)
        stats = findViewById(R.id.stats)
        statsBreakdown = findViewById(R.id.stats_breakdown)
        delayLabel = findViewById(R.id.delay_label)

        openSettings.setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.open_app_info).setOnClickListener { openAppInfo() }
        findViewById<Button>(R.id.reset_stats).setOnClickListener { prefs.resetStats() }

        bindSwitch(R.id.switch_enabled, prefs.skipEnabled) { prefs.skipEnabled = it }
        appSwitches = mapOf(
            WatchedApp.YOUTUBE to findViewById(R.id.switch_app_youtube),
            WatchedApp.YOUTUBE_MUSIC to findViewById(R.id.switch_app_youtube_music),
        )
        for ((app, s) in appSwitches) {
            s.isChecked = prefs.isAppEnabled(app)
            s.setOnCheckedChangeListener { _, checked -> prefs.setAppEnabled(app, checked) }
        }

        findViewById<TextView>(R.id.version).text = getString(R.string.version, versionName())
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyTestSettings(intent)
        findViewById<Switch>(R.id.switch_mute).isChecked = prefs.muteAds
    }

    /**
     * Debug builds only: lets the emulator test (.github/scripts/e2e.sh) switch muting on and
     * off with `am start ... --ez test_mute_ads true`. Release builds ignore it.
     */
    private fun applyTestSettings(intent: Intent?) {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable || intent == null || !intent.hasExtra(EXTRA_TEST_MUTE_ADS)) return
        prefs.muteAds = intent.getBooleanExtra(EXTRA_TEST_MUTE_ADS, false)
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
        if (key == null || Prefs.isStatsKey(key) || Prefs.isAppEnabledKey(key)) refresh()
    }

    private fun refresh() {
        val inSettings = isEnabledInSettings()
        val running = isRunningNow()
        when {
            running -> {
                statusTitle.setText(R.string.status_on)
                statusDetail.text = runningDetail()
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
        val installed = WatchedApp.entries.filter { isInstalled(it.packageName) }
        appsMissing.visibility = if (installed.isEmpty()) View.VISIBLE else View.GONE
        for ((app, s) in appSwitches) {
            val name = getString(appName(app))
            s.text = if (app in installed) name else getString(R.string.app_not_installed, name)
        }

        val count = prefs.skippedCount
        val last = prefs.lastSkipAt
        if (count == 0 || last == 0L) {
            stats.text = getString(R.string.stats_none)
            statsBreakdown.visibility = View.GONE
        } else {
            val ago = DateUtils.getRelativeTimeSpanString(
                last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            )
            stats.text = resources.getQuantityString(R.plurals.stats_count, count, count, ago)
            statsBreakdown.text = getString(
                R.string.stats_breakdown,
                prefs.skippedCount(WatchedApp.YOUTUBE),
                prefs.skippedCount(WatchedApp.YOUTUBE_MUSIC),
            )
            statsBreakdown.visibility = View.VISIBLE
        }
    }

    /** "Watching YouTube and YouTube Music." or just the one that's switched on. */
    private fun runningDetail(): String {
        val on = WatchedApp.entries.filter { prefs.isAppEnabled(it) }
        return when (on.size) {
            0 -> getString(R.string.status_on_no_apps)
            WatchedApp.entries.size -> getString(R.string.status_on_detail, getString(R.string.status_on_both))
            else -> getString(R.string.status_on_detail, on.joinToString { getString(appName(it)) })
        }
    }

    private fun appName(app: WatchedApp): Int = when (app) {
        WatchedApp.YOUTUBE -> R.string.app_youtube
        WatchedApp.YOUTUBE_MUSIC -> R.string.app_youtube_music
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

    private fun packageInfo(name: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(name, 0)
        }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** Needs the package listed in <queries> in AndroidManifest.xml on Android 11 and later. */
    private fun isInstalled(name: String): Boolean = packageInfo(name) != null

    private fun versionName(): String = packageInfo(packageName)?.versionName ?: "?"

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

    companion object {
        private const val EXTRA_TEST_MUTE_ADS = "test_mute_ads"
    }
}
