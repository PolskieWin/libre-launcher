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

class HibernateAfkManager(private val activity: Activity, private val rootContainer: ViewGroup) {

    private var afkOverlayView: View? = null
    private var afkStartTime = 0L
    private var isAfkActive = false

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

        // 1. Native throttle: 10 FPS, nice 10, relax affinity to efficiency cores
        LibreTelemetry.nativeSetAfk(true, 10)

        // 2. Dim screen brightness to minimum (0.01) to save OLED / display power
        try {
            val lp = activity.window.attributes
            lp.screenBrightness = 0.01f
            activity.window.attributes = lp
        } catch (t: Throwable) {
            // Ignore if window attributes cannot be set
        }

        // 3. Show full-screen OLED black power-saver view
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
        Toast.makeText(activity, "Hibernate AFK Mode Active (10 FPS)", Toast.LENGTH_SHORT).show()
    }

    fun exitAfk() {
        if (!isAfkActive) return
        isAfkActive = false
        rootContainer.removeCallbacks(timerRunnable)

        // 1. Restore native full performance: prime cores, nice -10, uncapped FPS
        LibreTelemetry.nativeSetAfk(false, 0)

        // 2. Restore normal display brightness
        try {
            val lp = activity.window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = lp
        } catch (t: Throwable) {
            // Ignore
        }

        // 3. Remove overlay view
        afkOverlayView?.let {
            rootContainer.removeView(it)
            afkOverlayView = null
        }
        afkTimerText = null

        Toast.makeText(activity, "Resumed full performance", Toast.LENGTH_SHORT).show()
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
            text = "Rendering throttled to 10 FPS • CPU cores relaxed\nScreen dimmed to minimize power and heat"
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
