package git.artdeell.mojo.telemetry

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import git.artdeell.mojo.Tools
import java.util.Locale

class PerformanceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    private val tvFps: TextView
    private val tvFrameTime: TextView
    private val tvOnePercentLow: TextView
    private val tvMemory: TextView
    private val tvAfkBadge: TextView

    private var isRunning = false
    private var lastFrameTimeNanos: Long = 0

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            updateMetrics()
            postDelayed(this, 250) // Update UI 4 times per second (smooth, 0% CPU impact)
        }
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.START
        isFocusable = false
        isClickable = false

        val pad = Tools.dpToPx(8f).toInt()
        setPadding(pad, pad, pad, pad)

        // Sleek rounded semi-transparent dark HUD
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#B3121218"))
            cornerRadius = Tools.dpToPx(8f)
            setStroke(Tools.dpToPx(1f).toInt(), Color.parseColor("#26FFFFFF"))
        }
        background = bg

        val monoTypeface = Typeface.MONOSPACE

        tvFps = TextView(context).apply {
            textSize = 14f
            setTypeface(monoTypeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#4CAF50"))
            text = "--- FPS"
        }

        tvFrameTime = TextView(context).apply {
            textSize = 10f
            typeface = monoTypeface
            setTextColor(Color.parseColor("#E0E0E0"))
            text = "FT: --.- ms"
        }

        tvOnePercentLow = TextView(context).apply {
            textSize = 10f
            typeface = monoTypeface
            setTextColor(Color.parseColor("#B0BEC5"))
            text = "1% Low: --.- FPS"
        }

        tvMemory = TextView(context).apply {
            textSize = 10f
            typeface = monoTypeface
            setTextColor(Color.parseColor("#90A4AE"))
            text = "RAM: ---- / ---- MB"
        }

        tvAfkBadge = TextView(context).apply {
            textSize = 10f
            setTypeface(monoTypeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#00E5FF"))
            text = "⚡ AFK HIBERNATE ACTIVE"
            visibility = View.GONE
        }

        addView(tvFps)
        addView(tvFrameTime)
        addView(tvOnePercentLow)
        addView(tvMemory)
        addView(tvAfkBadge)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return

        if (lastFrameTimeNanos > 0) {
            val deltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000f
            LibreTelemetry.nativeRecordFrame(deltaMs)
        }
        lastFrameTimeNanos = frameTimeNanos

        // Re-register for next frame
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        visibility = View.VISIBLE
        lastFrameTimeNanos = 0
        Choreographer.getInstance().postFrameCallback(this)
        post(updateRunnable)
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        visibility = View.GONE
        Choreographer.getInstance().removeFrameCallback(this)
        removeCallbacks(updateRunnable)
    }

    private fun updateMetrics() {
        val snap = LibreTelemetry.getSnapshot()

        // FPS Coloring
        val fpsColor = when {
            snap.fps >= 55f -> Color.parseColor("#4CAF50") // Green
            snap.fps >= 30f -> Color.parseColor("#FFC107") // Amber
            else -> Color.parseColor("#F44336")            // Red
        }
        tvFps.setTextColor(fpsColor)
        tvFps.text = String.format(Locale.ROOT, "%.1f FPS", snap.fps)

        tvFrameTime.text = String.format(
            Locale.ROOT,
            "FT: %.1f ms (min %.1f / max %.1f)",
            snap.frameTimeMs, snap.minFrameTimeMs, snap.maxFrameTimeMs
        )

        tvOnePercentLow.text = String.format(
            Locale.ROOT,
            "1%% Low: %.1f FPS",
            snap.onePercentLow
        )

        if (snap.ramTotalMb > 0) {
            tvMemory.text = String.format(
                Locale.ROOT,
                "RAM: %d / %d MB",
                snap.ramUsedMb, snap.ramTotalMb
            )
        } else {
            tvMemory.text = String.format(
                Locale.ROOT,
                "RAM: %d MB",
                snap.ramUsedMb
            )
        }

        if (snap.isAfk) {
            tvAfkBadge.visibility = View.VISIBLE
        } else {
            tvAfkBadge.visibility = View.GONE
        }
    }
}
