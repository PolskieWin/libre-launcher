package git.artdeell.mojo.telemetry

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import git.artdeell.mojo.Tools
import java.util.Locale

class HibernateAfkManager(
    private val activity: Activity,
    private val rootContainer: ViewGroup
) {

    private var afkOverlayView: View? = null
    private var afkStartTime = 0L
    private var isAfkActive = false

    var onSurfaceDownscale: ((scaleFactor: Float) -> Unit)? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isAfkActive) return
            val elapsedSec = (SystemClock.elapsedRealtime() - afkStartTime) / 1000
            val min = elapsedSec / 60
            val sec = elapsedSec % 60
            afkTimerText?.text = String.format(Locale.ROOT, "AFK Duration: %02d:%02d", min, sec)
            rootContainer.postDelayed(this, 1000)
        }
    }

    private var afkTimerText: TextView? = null

    fun toggleAfk() {
        if (isAfkActive) {
            exitAfk()
        } else {
            enterAfk()
        }
    }

    fun isAfk(): Boolean = isAfkActive

    fun enterAfk() {
        if (isAfkActive) return
        isAfkActive = true
        afkStartTime = SystemClock.elapsedRealtime()

        // 1. Rust frame pacing & nice priority throttle: 5 FPS (200ms frame budget)
        LibreTelemetry.nativeSetHibernate(true, 5)

        // 2. Downscale surface resolution to 0.25x to minimize GPU render load
        try {
            onSurfaceDownscale?.invoke(0.25f)
        } catch (t: Throwable) {
            // Ignore if surface cannot be downscaled
        }

        // 3. Dim screen brightness to minimum (0.01) to save AMOLED pixel power
        try {
            val lp = activity.window.attributes
            lp.screenBrightness = 0.01f
            activity.window.attributes = lp
        } catch (t: Throwable) {
            // Ignore
        }

        // 4. Show full-screen OLED black power-saver view
        val overlay = createAfkOverlayView()
        afkOverlayView = overlay
        rootContainer.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        rootContainer.post(timerRunnable)
        Toast.makeText(activity, "Hibernate Mode Active (5 FPS • 0.25x Res)", Toast.LENGTH_SHORT).show()
    }

    fun exitAfk() {
        if (!isAfkActive) return
        isAfkActive = false
        rootContainer.removeCallbacks(timerRunnable)

        // 1. Restore native full performance in Rust: uncapped FPS, prime cores, nice -10
        LibreTelemetry.nativeSetHibernate(false, 60)

        // 2. Restore 1.0x surface resolution
        try {
            onSurfaceDownscale?.invoke(1.0f)
        } catch (t: Throwable) {
            // Ignore
        }

        // 3. Restore normal display brightness
        try {
            val lp = activity.window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = lp
        } catch (t: Throwable) {
            // Ignore
        }

        // 4. Remove overlay view
        afkOverlayView?.let {
            rootContainer.removeView(it)
            afkOverlayView = null
        }
        afkTimerText = null

        Toast.makeText(activity, "Resumed full performance (1.0x Res)", Toast.LENGTH_SHORT).show()
    }

    fun onPauseOrPip() {
        // Optional auto-hibernate when backgrounded or entering PiP
        if (!isAfkActive) {
            enterAfk()
        }
    }

    fun onThermalThresholdExceeded() {
        if (!isAfkActive) {
            enterAfk()
            Toast.makeText(activity, "Thermal Headroom > 95%! Auto-Hibernate Activated", Toast.LENGTH_LONG).show()
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
            text = "🌙"
            textSize = 48f
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
