package git.artdeell.mojo.telemetry

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import git.artdeell.mojo.Tools
import java.io.File
import java.util.Locale

/**
 * Thermal-Aware Hibernate & AFK Governor for Libre Launcher.
 *
 * Automatically monitors device SoC thermal headroom, battery temperatures,
 * and idle state to dynamically adapt render FPS, surface resolution scaling,
 * and background polling to protect mobile hardware from thermal throttling.
 */
class HibernateAfkManager(
    private val activity: Activity,
    private val rootContainer: ViewGroup
) {
    companion object {
        private const val TAG = "HibernateAfkManager"
    }

    enum class ThermalProfile(
        val title: String,
        val targetFps: Int,
        val scaleFactor: Float,
        val pollingIntervalMs: Long
    ) {
        FULL_PERFORMANCE("Full Performance", 0, 1.0f, 10000L),
        WARM_MITIGATION("Warm Mitigation", 45, 0.85f, 6000L),
        THERMAL_PROTECTION("Thermal Protection", 30, 0.70f, 4000L),
        CRITICAL_PROTECTION("Critical Protection", 15, 0.50f, 2500L),
        MANUAL_AFK("Hibernate AFK", 5, 0.25f, 5000L)
    }

    private var afkOverlayView: View? = null
    private var afkStartTime = 0L
    private var isManualAfkActive = false

    private var currentProfile = ThermalProfile.FULL_PERFORMANCE
    private var isMonitoringActive = false

    private val powerManager by lazy {
        try {
            activity.getSystemService(Context.POWER_SERVICE) as? PowerManager
        } catch (_: Throwable) {
            null
        }
    }

    private var thermalListener: Any? = null

    var onSurfaceDownscale: ((scaleFactor: Float) -> Unit)? = null

    // Adaptive thermal poller runnable
    private val thermalPollerRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoringActive) return
            evaluateAndApplyThermalState()
            val interval = if (isManualAfkActive) {
                ThermalProfile.MANUAL_AFK.pollingIntervalMs
            } else {
                currentProfile.pollingIntervalMs
            }
            rootContainer.postDelayed(this, interval)
        }
    }

    // AFK duration timer runnable
    private val afkTimerRunnable = object : Runnable {
        override fun run() {
            if (!isManualAfkActive) return
            val elapsedSec = (SystemClock.elapsedRealtime() - afkStartTime) / 1000
            val min = elapsedSec / 60
            val sec = elapsedSec % 60
            afkTimerText?.text = String.format(Locale.ROOT, "AFK Duration: %02d:%02d", min, sec)
            rootContainer.postDelayed(this, 1000)
        }
    }

    private var afkTimerText: TextView? = null

    fun startThermalMonitoring() {
        if (isMonitoringActive) return
        isMonitoringActive = true

        // Register Android 10+ (API 29+) reactive thermal listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            try {
                val listener = PowerManager.OnThermalStatusChangedListener { _ ->
                    activity.runOnUiThread {
                        evaluateAndApplyThermalState()
                    }
                }
                powerManager?.addThermalStatusListener(activity.mainExecutor, listener)
                thermalListener = listener
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to register thermal status listener: ${t.message}")
            }
        }

        rootContainer.post(thermalPollerRunnable)
    }

    fun stopThermalMonitoring() {
        isMonitoringActive = false
        rootContainer.removeCallbacks(thermalPollerRunnable)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermalListener != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                powerManager?.removeThermalStatusListener(thermalListener as PowerManager.OnThermalStatusChangedListener)
            } catch (_: Throwable) {}
            thermalListener = null
        }
    }

    fun toggleAfk() {
        if (isManualAfkActive) {
            exitAfk()
        } else {
            enterAfk()
        }
    }

    fun isAfk(): Boolean = isManualAfkActive

    fun getCurrentProfile(): ThermalProfile = currentProfile

    fun enterAfk() {
        if (isManualAfkActive) return
        isManualAfkActive = true
        afkStartTime = SystemClock.elapsedRealtime()
        applyProfile(ThermalProfile.MANUAL_AFK, force = true)

        // Dim screen brightness to minimum (0.01) to save AMOLED pixel power
        try {
            val lp = activity.window.attributes
            lp.screenBrightness = 0.01f
            activity.window.attributes = lp
        } catch (_: Throwable) {}

        // Show full-screen OLED black power-saver view
        val overlay = createAfkOverlayView()
        afkOverlayView = overlay
        rootContainer.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        rootContainer.post(afkTimerRunnable)
        Toast.makeText(activity, "Hibernate AFK Active (5 FPS • 0.25x Res)", Toast.LENGTH_SHORT).show()
    }

    fun exitAfk() {
        if (!isManualAfkActive) return
        isManualAfkActive = false
        rootContainer.removeCallbacks(afkTimerRunnable)

        // Restore normal display brightness
        try {
            val lp = activity.window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = lp
        } catch (_: Throwable) {}

        // Remove overlay view
        afkOverlayView?.let {
            rootContainer.removeView(it)
            afkOverlayView = null
        }
        afkTimerText = null

        // Re-evaluate based on current thermal temperature
        evaluateAndApplyThermalState(force = true)
        Toast.makeText(activity, "Resumed Gameplay (${currentProfile.title})", Toast.LENGTH_SHORT).show()
    }

    fun onPauseOrPip() {
        if (!isManualAfkActive) {
            enterAfk()
        }
    }

    fun readBatteryTemperature(): Float {
        return try {
            val intent = activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (tempTenths > 0) tempTenths / 10.0f else 0.0f
        } catch (_: Throwable) {
            0.0f
        }
    }

    fun readSocThermalStatus(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            try {
                return powerManager?.currentThermalStatus ?: 0
            } catch (_: Throwable) {}
        }
        return 0
    }

    fun readSocThermalHeadroom(): Float {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && powerManager != null) {
            try {
                return powerManager?.getThermalHeadroom(10) ?: 0.0f
            } catch (_: Throwable) {}
        }
        return 0.0f
    }

    private fun readSysfsThermalTemp(): Float {
        for (i in 0..5) {
            val f = File("/sys/class/thermal/thermal_zone$i/temp")
            if (f.exists() && f.canRead()) {
                try {
                    val line = f.readText().trim()
                    val raw = line.toFloatOrNull() ?: continue
                    val deg = if (raw > 1000.0f) raw / 1000.0f else raw
                    if (deg in 20.0f..115.0f) {
                        return deg
                    }
                } catch (_: Throwable) {}
            }
        }
        return 0.0f
    }

    private fun evaluateAndApplyThermalState(force: Boolean = false) {
        if (isManualAfkActive) return

        val batteryTemp = readBatteryTemperature()
        val socStatus = readSocThermalStatus()
        val headroom = readSocThermalHeadroom()
        val sysfsTemp = readSysfsThermalTemp()

        val evaluated = when {
            batteryTemp >= 48.0f || socStatus >= 4 || headroom >= 1.25f || sysfsTemp >= 78.0f ->
                ThermalProfile.CRITICAL_PROTECTION

            batteryTemp >= 44.5f || socStatus == 3 || headroom >= 1.0f || sysfsTemp >= 68.0f ->
                ThermalProfile.THERMAL_PROTECTION

            batteryTemp >= 41.5f || socStatus == 2 || headroom >= 0.85f || sysfsTemp >= 58.0f ->
                ThermalProfile.WARM_MITIGATION

            else -> ThermalProfile.FULL_PERFORMANCE
        }

        if (evaluated != currentProfile || force) {
            val previous = currentProfile
            applyProfile(evaluated, force)

            if (evaluated != ThermalProfile.FULL_PERFORMANCE && previous == ThermalProfile.FULL_PERFORMANCE) {
                val tempStr = if (batteryTemp > 0.0f) {
                    String.format(Locale.ROOT, "%.1f°C", batteryTemp)
                } else if (sysfsTemp > 0.0f) {
                    String.format(Locale.ROOT, "%.1f°C", sysfsTemp)
                } else {
                    "high"
                }
                Toast.makeText(
                    activity,
                    "Thermal Governor: ${evaluated.title} ($tempStr • ${evaluated.targetFps} FPS)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun applyProfile(profile: ThermalProfile, force: Boolean) {
        currentProfile = profile

        // 1. Native Rust Frame Pacing
        val isThrottled = profile != ThermalProfile.FULL_PERFORMANCE
        LibreTelemetry.nativeSetHibernate(isThrottled, profile.targetFps)

        // 2. Dynamic Resolution Scaling
        try {
            onSurfaceDownscale?.invoke(profile.scaleFactor)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to apply dynamic surface downscale: ${t.message}")
        }
    }

    private fun createAfkOverlayView(): View {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK) // Pure AMOLED black
            isClickable = true
            isFocusable = true
            setOnClickListener {
                exitAfk()
            }
        }

        val tvIcon = TextView(activity).apply {
            text = "AFK"
            textSize = 32f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.parseColor("#F2BC90")) // Copper accent
            gravity = Gravity.CENTER
        }

        val tvTitle = TextView(activity).apply {
            text = "Hibernate / Low-Power AFK Active"
            textSize = 18f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val pad = Tools.dpToPx(12f).toInt()
            setPadding(pad, pad, pad, Tools.dpToPx(4f).toInt())
        }

        val tvDesc = TextView(activity).apply {
            text = "Render locked to 5 FPS (200ms) • Resolution 0.25x\nCPU cores relaxed • Screen dimmed"
            textSize = 12f
            setTextColor(Color.parseColor("#9E9E9E"))
            gravity = Gravity.CENTER
        }

        val tvTimer = TextView(activity).apply {
            text = "AFK Duration: 00:00"
            textSize = 13f
            setTypeface(Typeface.MONOSPACE)
            setTextColor(Color.parseColor("#00E5FF"))
            gravity = Gravity.CENTER
            val pad = Tools.dpToPx(16f).toInt()
            setPadding(pad, pad, pad, pad)
        }
        afkTimerText = tvTimer

        val tvWakePrompt = TextView(activity).apply {
            text = "Tap anywhere to wake up"
            textSize = 14f
            setTypeface(Typeface.DEFAULT, Typeface.ITALIC)
            setTextColor(Color.parseColor("#80FFFFFF"))
            gravity = Gravity.CENTER
            val pad = Tools.dpToPx(24f).toInt()
            setPadding(pad, pad, pad, pad)
        }

        layout.addView(tvIcon)
        layout.addView(tvTitle)
        layout.addView(tvDesc)
        layout.addView(tvTimer)
        layout.addView(tvWakePrompt)

        return layout
    }
}
