package git.artdeell.mojo.telemetry

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import git.artdeell.mojo.Tools
import java.util.Locale

@SuppressLint("ClickableViewAccessibility")
class TelemetryOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    private val tvDragHandle: TextView
    private val tvMetrics: TextView
    private val btnHibernate: TextView

    var onHibernateToggle: (() -> Unit)? = null

    private var isRunning = false
    private var lastFrameTimeNanos: Long = 0

    // Touch Dragging state
    private var dX = 0f
    private var dY = 0f
    private var startRawX = 0f
    private var startRawY = 0f
    private var isDragging = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            updateMetrics()
            postDelayed(this, 500) // Poll every 500ms
        }
    }

    init {
        visibility = View.GONE
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        val padHorizontal = Tools.dpToPx(12f).toInt()
        val padVertical = Tools.dpToPx(6f).toInt()
        setPadding(padHorizontal, padVertical, padHorizontal, padVertical)

        // Floating pill design
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#E61E1611"))
            cornerRadius = Tools.dpToPx(18f)
            setStroke(Tools.dpToPx(1.2f).toInt(), Color.parseColor("#4DF2BC90"))
        }
        background = bg

        val monoTypeface = Typeface.MONOSPACE

        // Drag handle indicator
        tvDragHandle = TextView(context).apply {
            text = "::: "
            textSize = 12f
            setTextColor(Color.parseColor("#99F2BC90"))
            gravity = Gravity.CENTER
        }

        // Compact metrics: FPS | 1% LOW | RAM MB | TEMP / HEADROOM
        tvMetrics = TextView(context).apply {
            textSize = 11f
            setTypeface(monoTypeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#F7E2D3"))
            text = "--.- FPS | 1% --.- | --- MB | --"
        }

        // One-tap Hibernate toggle button
        btnHibernate = TextView(context).apply {
            text = " AFK "
            textSize = 10f
            setTypeface(monoTypeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#F2BC90"))
            val btnPadH = Tools.dpToPx(8f).toInt()
            val btnPadV = Tools.dpToPx(3f).toInt()
            setPadding(btnPadH, btnPadV, btnPadH, btnPadV)

            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#26F2BC90"))
                cornerRadius = Tools.dpToPx(12f)
                setStroke(Tools.dpToPx(1f).toInt(), Color.parseColor("#66F2BC90"))
            }
            background = btnBg

            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = Tools.dpToPx(8f).toInt()
            }
            layoutParams = lp

            setOnClickListener {
                onHibernateToggle?.invoke()
            }
        }

        addView(tvDragHandle)
        addView(tvMetrics)
        addView(btnHibernate)

        setupDragTouch()
    }

    private fun setupDragTouch() {
        setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    startRawX = event.rawX
                    startRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moveX = Math.abs(event.rawX - startRawX)
                    val moveY = Math.abs(event.rawY - startRawY)
                    if (moveX > 10 || moveY > 10) {
                        isDragging = true
                        view.animate()
                            .x(event.rawX + dX)
                            .y(event.rawY + dY)
                            .setDuration(0)
                            .start()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return
        if (lastFrameTimeNanos > 0) {
            val deltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000f
            LibreTelemetry.nativeRecordFrame(deltaMs)
        }
        lastFrameTimeNanos = frameTimeNanos
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        visibility = View.VISIBLE
        lastFrameTimeNanos = 0
        Choreographer.getInstance().postFrameCallback(this)
        post(pollRunnable)
    }

    fun stop() {
        isRunning = false
        visibility = View.GONE
        Choreographer.getInstance().removeFrameCallback(this)
        removeCallbacks(pollRunnable)
    }

    fun updateMetrics() {
        val snap = LibreTelemetry.getSnapshot()

        val fpsColor = when {
            snap.fps >= 55f -> "#A5D6A7"
            snap.fps >= 30f -> "#F2BC90"
            else -> "#EF9A9A"
        }
        tvMetrics.setTextColor(Color.parseColor(fpsColor))

        // Thermal Headroom format
        val thermalStr = if (snap.thermalHeadroom > 0f) {
            val percent = (snap.thermalHeadroom * 100).toInt()
            if (snap.thermalHeadroom >= 0.95f) {
                "${percent}% HIGH"
            } else if (snap.thermalHeadroom >= 0.80f) {
                "${percent}% WARN"
            } else {
                "${percent}%"
            }
        } else {
            "NORM"
        }

        if (snap.isHibernate) {
            tvMetrics.text = String.format(
                Locale.ROOT,
                "%.1f FPS | 1%% %.1f | %dMB | %s [AFK 5 FPS]",
                snap.fps, snap.onePercentLow, snap.ramUsedMb, thermalStr
            )
            btnHibernate.text = " WAKE "
            btnHibernate.setTextColor(Color.parseColor("#F7E2D3"))
            (btnHibernate.background as? GradientDrawable)?.apply {
                setColor(Color.parseColor("#4DF2BC90"))
                setStroke(Tools.dpToPx(1f).toInt(), Color.parseColor("#F2BC90"))
            }
        } else {
            tvMetrics.text = String.format(
                Locale.ROOT,
                "%.1f FPS | 1%% %.1f | %dMB | %s",
                snap.fps, snap.onePercentLow, snap.ramUsedMb, thermalStr
            )
            btnHibernate.text = " AFK "
            btnHibernate.setTextColor(Color.parseColor("#F2BC90"))
            (btnHibernate.background as? GradientDrawable)?.apply {
                setColor(Color.parseColor("#26F2BC90"))
                setStroke(Tools.dpToPx(1f).toInt(), Color.parseColor("#66F2BC90"))
            }
        }
    }
}
