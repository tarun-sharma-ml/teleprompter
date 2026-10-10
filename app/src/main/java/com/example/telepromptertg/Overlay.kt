package com.example.telepromptertg

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Thin progress line drawn at the bottom of the script box. */
private class ProgressLine(context: Context) : View(context) {
    var fraction = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    private val track = Paint().apply { color = Color.argb(60, 255, 255, 255) }
    private val fill = Paint().apply { color = Color.rgb(46, 204, 154) }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), track)
        canvas.drawRect(0f, 0f, width * fraction, height.toFloat(), fill)
    }
}

/**
 * Floating teleprompter panel drawn over other apps (e.g. the camera app).
 * All public functions can be called from any thread.
 */
@SuppressLint("ClickableViewAccessibility") // the panel's touch areas are drag handles, not buttons
class Overlay(ctx: Context, private val prefs: Prefs) {

    companion object {
        const val DEFAULT_TEXT =
            "Welcome to Teleprompter!\n\n" +
                    "Enter or paste your script in the app, then tap Start Teleprompter. " +
                    "The text will float on screen over your camera or any other application.\n\n" +
                    "Use the controls below to play, pause, change speed, adjust font size, or drag the panel anywhere on screen."
        private const val VIEWPORT_DP = 170
        private val DIM = Color.argb(140, 255, 255, 255)
        private val ACCENT = Color.rgb(46, 204, 154)
    }

    private val main = Handler(Looper.getMainLooper())
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val themedContext = ContextThemeWrapper(ctx, android.R.style.Theme_DeviceDefault)
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val screenW = ctx.resources.displayMetrics.widthPixels
    private val screenH = ctx.resources.displayMetrics.heightPixels
    private val panelW = minOf((screenW * 0.92).toInt(), dp(520))

    private val lp = WindowManager.LayoutParams(
        panelW,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (screenW - panelW) / 2
        y = (screenH * 0.06).toInt()
    }

    private val root = LinearLayout(themedContext)
    private val scroll = ScrollView(themedContext)
    private val tv = TextView(themedContext)
    private val leftMarker = View(themedContext)
    private val rightMarker = View(themedContext)
    private val progress = ProgressLine(themedContext)
    private val playBtn: TextView

    private var attached = false
    private var playing = false
    private var offset = 0.0
    private var maxOffset = 1.0
    private var lastTh = 0
    private var hiLine = -1
    private var words = 1
    private var wpm = prefs.wpm
    private var size = prefs.size
    private var currentText = ""
    private var last = 0L
    private val hiSpan = ForegroundColorSpan(Color.WHITE)

    private val tick = object : Runnable {
        override fun run() {
            if (!attached) return
            val now = SystemClock.uptimeMillis()
            val dt = (now - last) / 1000.0
            last = now
            val l: Layout? = tv.layout
            if (l != null && l.lineCount > 0) {
                val th = l.height
                if (th != lastTh) {
                    if (lastTh > 0) offset = offset * th / lastTh // keep place when font size changes
                    lastTh = th
                }
                maxOffset = maxOf((th - tv.lineHeight).toDouble(), 1.0)
                if (playing) {
                    offset += wpm / 60.0 * th / words * dt
                    if (offset >= maxOffset) {
                        offset = maxOffset
                        playing = false
                        updatePlayLabel()
                    }
                }
                offset = offset.coerceIn(0.0, maxOffset)
                scroll.scrollTo(0, offset.toInt())
                highlight(l)
                val f = (offset / maxOffset).toFloat()
                if (abs(f - progress.fraction) > 0.0005f) progress.fraction = f
            }
            main.postDelayed(this, 16)
        }
    }

    init {
        val pad = dp(10)
        root.orientation = LinearLayout.VERTICAL
        root.background = GradientDrawable().apply {
            setColor(Color.argb(224, 18, 18, 18))
            cornerRadius = dp(18).toFloat()
        }
        root.setPadding(pad, dp(2), pad, dp(8))

        // --- drag handle (moves the whole panel) ---
        val handleWrap = FrameLayout(themedContext)
        val pill = View(themedContext)
        pill.background = GradientDrawable().apply {
            setColor(Color.argb(110, 255, 255, 255))
            cornerRadius = dp(2).toFloat()
        }
        handleWrap.addView(pill, FrameLayout.LayoutParams(dp(44), dp(4), Gravity.CENTER))
        root.addView(handleWrap, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)))
        var dx = 0f
        var dy = 0f
        handleWrap.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dx = lp.x - e.rawX
                    dy = lp.y - e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (e.rawX + dx).toInt()
                    lp.y = (e.rawY + dy).toInt()
                    if (attached) wm.updateViewLayout(root, lp)
                }
            }
            true
        }

        // --- script viewport ---
        val vpH = dp(VIEWPORT_DP)
        val readY = (vpH * 0.38).toInt() // y of the reading line inside the viewport
        tv.gravity = Gravity.CENTER_HORIZONTAL
        tv.setTextColor(DIM)
        tv.setLineSpacing(0f, 1.15f)
        tv.setPadding(dp(14), readY, dp(14), vpH - readY)
        scroll.isVerticalScrollBarEnabled = false
        scroll.isVerticalFadingEdgeEnabled = true
        scroll.setFadingEdgeLength(dp(26))
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.addView(tv)
        // dragging on the text scrubs through the script
        var lastY = 0f
        scroll.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> lastY = e.rawY
                MotionEvent.ACTION_MOVE -> {
                    offset = (offset - (e.rawY - lastY)).coerceIn(0.0, maxOffset)
                    lastY = e.rawY
                }
            }
            true
        }

        leftMarker.setBackgroundColor(ACCENT)
        rightMarker.setBackgroundColor(ACCENT)
        val frame = FrameLayout(themedContext)
        frame.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, vpH))
        frame.addView(
            leftMarker,
            FrameLayout.LayoutParams(dp(3), dp(30), Gravity.TOP or Gravity.START).apply {
                topMargin = readY
                leftMargin = dp(2)
            },
        )
        frame.addView(
            rightMarker,
            FrameLayout.LayoutParams(dp(3), dp(30), Gravity.TOP or Gravity.END).apply {
                topMargin = readY
                rightMargin = dp(2)
            },
        )
        root.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, vpH))
        root.addView(
            progress,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            },
        )

        // --- controls ---
        playBtn = btn("▶", 20f) { togglePlay() }
        root.addView(
            row(
                btn("✕", 18f) { hide() },
                btn("−5s", 14f) { seek(-5.0) },
                playBtn,
                btn("+5s", 14f) { seek(5.0) },
            ),
        )
        root.addView(
            row(
                btn("Slower", 13f) { changeWpm(-10) },
                btn("Faster", 13f) { changeWpm(10) },
                btn("A−", 15f) { changeSize(-2) },
                btn("A+", 15f) { changeSize(2) },
            ),
        )

        applySize()
        applyText(prefs.text.trim().ifEmpty { DEFAULT_TEXT })
    }

    // ------------------------------------------------------------ UI helpers
    private fun btn(label: String, sizeSp: Float, onClick: () -> Unit): TextView {
        val b = TextView(themedContext)
        b.text = label
        b.setTextColor(Color.WHITE)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        b.gravity = Gravity.CENTER
        b.minHeight = dp(40)
        b.background = GradientDrawable().apply {
            setColor(Color.argb(60, 255, 255, 255))
            cornerRadius = dp(12).toFloat()
        }
        b.setOnClickListener { onClick() }
        b.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        return b
    }

    private fun row(vararg views: View): LinearLayout {
        val r = LinearLayout(themedContext)
        r.orientation = LinearLayout.HORIZONTAL
        for (v in views) r.addView(v)
        return r
    }

    private fun updatePlayLabel() {
        playBtn.text = if (playing) "❚❚" else "▶"
    }

    private fun applySize() {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        val h = tv.lineHeight
        (leftMarker.layoutParams as FrameLayout.LayoutParams).height = h
        (rightMarker.layoutParams as FrameLayout.LayoutParams).height = h
        leftMarker.requestLayout()
        rightMarker.requestLayout()
    }

    private fun applyText(t: String) {
        currentText = t
        words = maxOf(t.split(Regex("\\s+")).count { it.isNotEmpty() }, 1)
        tv.setText(t, TextView.BufferType.SPANNABLE)
        offset = 0.0
        lastTh = 0
        hiLine = -1
        playing = false
        scroll.scrollTo(0, 0)
        updatePlayLabel()
    }

    private fun highlight(l: Layout) {
        val line = l.getLineForVertical((offset + tv.lineHeight / 2.0).toInt()).coerceIn(0, l.lineCount - 1)
        if (line == hiLine) return
        hiLine = line
        val sp = tv.text as? Spannable ?: return
        sp.removeSpan(hiSpan)
        sp.setSpan(hiSpan, l.getLineStart(line), l.getLineEnd(line), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    // ------------------------------------------------------------ thread helpers
    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask<T> { block() }
        main.post(task)
        return task.get(5, TimeUnit.SECONDS)
    }

    private fun act(block: () -> Unit) {
        onMain(block)
    }

    // ------------------------------------------------------------ public API
    fun show() = act {
        if (!attached) {
            wm.addView(root, lp)
            attached = true
            last = SystemClock.uptimeMillis()
            main.post(tick)
        }
    }

    fun hide() = act {
        if (attached) {
            playing = false
            updatePlayLabel()
            main.removeCallbacks(tick)
            wm.removeView(root)
            attached = false
        }
    }

    fun setScript(text: String) = act {
        val t = text.trim().ifEmpty { DEFAULT_TEXT }
        if (t != currentText) applyText(t) // unchanged text keeps the current position
    }

    fun setPlaying(play: Boolean) = act {
        if (play) {
            if (!attached) show()
            if (offset >= maxOffset - 1) offset = 0.0
        }
        playing = play
        updatePlayLabel()
    }

    fun togglePlay() = act { setPlaying(!playing) }

    fun seek(seconds: Double) = act {
        val th = maxOf(lastTh, 1)
        offset = (offset + seconds * wpm / 60.0 * th / words).coerceIn(0.0, maxOffset)
    }

    fun setWpm(value: Int) = act {
        wpm = value.coerceIn(40, 400)
        prefs.wpm = wpm
    }

    fun changeWpm(delta: Int) = act { setWpm(wpm + delta) }

    fun setSize(value: Int) = act {
        size = value.coerceIn(12, 48)
        prefs.size = size
        applySize()
    }

    fun changeSize(delta: Int) = act { setSize(size + delta) }
}