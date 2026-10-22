package com.example.telepromptertg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.view.animation.PathInterpolator
import android.view.View

/** Draws the staged startup animation from the supplied HTML reference. */
class StartupAnimationView(context: Context) : View(context) {

    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** Returns the real header rule's rect (px, relative to this view); the bar settles onto it. */
    var targetProvider: (() -> RectF?)? = null

    private val backgroundPaint = paint(COLOR_SCREEN)
    private val amberPaint = paint(COLOR_AMBER)
    private val mutedPaint = paint(COLOR_MUTED)
    private val whitePaint = paint(COLOR_WHITE)
    private val descriptionPaint = paint(COLOR_DESCRIPTION).apply { textSize = sp(11f) }
    private val hintPaint = paint(COLOR_HINT).apply { textSize = sp(8f) }
    private val titlePaint = paint(COLOR_WHITE).apply {
        textSize = sp(22f)
        typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL,
        )
    }
    private val buttonTextPaint = paint(COLOR_BEZEL).apply {
        textSize = sp(13f)
        typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL,
        )
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_AMBER
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_OUTLINE
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val time = progress * DURATION_MS
        // Dark backdrop fades out while the real screen fades in underneath.
        val backdrop = 1f - ease((time - 4300f) / 900f)
        canvas.drawColor(Color.argb((backdrop * 255).toInt(), 0x1E, 0x23, 0x27))

        val unit = width / ART_WIDTH
        val artTop = (height / unit - ART_HEIGHT) / 2f
        canvas.save()
        canvas.scale(unit, unit)
        canvas.translate(0f, artTop)
        drawScreen(canvas, time)
        canvas.restore()
        drawBar(canvas, time, unit, artTop)
    }

    private fun drawScreen(canvas: Canvas, time: Float) {
        drawOpeningMark(canvas, time)
        drawScriptLines(canvas, time)
        drawReadingMarkers(canvas, time)
    }

    private fun drawOpeningMark(canvas: Canvas, time: Float) {
        // Glow ring is centred on the screen (HTML: left 59, top 138, 110x110) and sits behind the T.
        val pulse = ((time - 1400f) / 800f).coerceIn(0f, 1f)
        if (pulse > 0f && pulse < 1f) {
            val p = easeOut(pulse)
            ringPaint.alpha = (lerp(0.9f, 0f, p) * 255).toInt()
            ringPaint.strokeWidth = 3f * lerp(0.5f, 1.9f, p)
            canvas.drawCircle(114f, 193f, lerp(27.5f, 104.5f, p), ringPaint)
        }

        // T stem: four short bars that drop in, then slide away when the script appears.
        repeat(4) { index ->
            val start = 500f + index * 150f
            val appear = ease((time - start) / 350f)
            val leave = ease((time - 2200f) / 350f)
            val visible = appear * (1f - leave)
            if (visible <= 0f) return@repeat
            val width = lerp(24f * 0.2f, 24f, appear)
            val y = 180f + index * 16f + (1f - appear) * -16f + leave * 10f
            val opacity = floatArrayOf(0.9f, 0.65f, 0.45f, 0.25f)[index]
            whitePaint.alpha = (visible * opacity * 255).toInt()
            canvas.drawRoundRect(114f - width / 2f, y, 114f + width / 2f, y + 8f, 4f, 4f, whitePaint)
        }
        whitePaint.alpha = 255
    }

    private fun drawBar(canvas: Canvas, time: Float, unit: Float, artTop: Float) {
        val slide = easeOut((time - 60f) / 600f)
        val fadeIn = ((time - 60f) / 400f).coerceIn(0f, 1f)
        val drop = ease((time - 2200f) / 500f)
        val widen = ease((time - 2800f) / 500f)
        val settle = ease((time - 3900f) / 500f)

        val x = lerp(lerp(-156f, 64f, slide), 39f, widen)
        val w = lerp(100f, 150f, widen)
        val y = lerp(150f, 192f, drop)
        // Final resting place = the real header rule, so the hand-off is pixel-exact.
        val end = targetProvider?.invoke()
            ?: RectF(59f * unit, (60f + artTop) * unit, 169f * unit, (64f + artTop) * unit)
        val left = lerp(x * unit, end.left, settle)
        val right = lerp((x + w) * unit, end.right, settle)
        val top = lerp((y + artTop) * unit, end.top, settle)
        val bottom = lerp((y + 16f + artTop) * unit, end.bottom, settle)
        val radius = (bottom - top) / 2f
        amberPaint.alpha = (fadeIn * 255).toInt()
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, amberPaint)
        amberPaint.alpha = 255
    }

    private fun drawScriptLines(canvas: Canvas, time: Float) {
        val reveal = easeOut((time - 2200f) / 900f)
        val fadeIn = ((time - 2200f) / 500f).coerceIn(0f, 1f)
        if (fadeIn <= 0f) return

        val fade = fadeIn * (1f - ease((time - 3900f) / 500f))
        val lineY = floatArrayOf(-89f, -61f, -33f, 23f, 51f, 79f)
        val lineWidth = floatArrayOf(56f, 70f, 104f, 104f, 70f, 56f)
        val alpha = intArrayOf(38, 77, 153, 153, 77, 38)
        lineY.forEachIndexed { index, offset ->
            val y = 200f + offset + (1f - reveal) * 170f
            whitePaint.alpha = (alpha[index] * fade).toInt()
            val barWidth = lineWidth[index]
            canvas.drawRoundRect(
                114f - barWidth / 2f,
                y,
                114f + barWidth / 2f,
                y + 10f,
                5f,
                5f,
                whitePaint,
            )
        }
        whitePaint.alpha = 255
    }

    private fun drawReadingMarkers(canvas: Canvas, time: Float) {
        val slide = easeOut((time - 2800f) / 450f)
        val fadeIn = ((time - 2800f) / 300f).coerceIn(0f, 1f)
        val fadeOut = 1f - ((time - 3900f) / 300f).coerceIn(0f, 1f)
        val alpha = (fadeIn * fadeOut * 255).toInt()
        if (alpha <= 0) return

        val cy = 200f
        val leftX = lerp(-20f, 20f, slide)   // base edge of the left arrow
        val rightX = lerp(235f, 195f, slide) // tip of the right arrow
        amberPaint.alpha = alpha

        val leftMarker = Path().apply {
            moveTo(leftX, cy - 9f)
            lineTo(leftX + 13f, cy)
            lineTo(leftX, cy + 9f)
            close()
        }
        canvas.drawPath(leftMarker, amberPaint)
        val rightMarker = Path().apply {
            moveTo(rightX + 13f, cy - 9f)
            lineTo(rightX, cy)
            lineTo(rightX + 13f, cy + 9f)
            close()
        }
        canvas.drawPath(rightMarker, amberPaint)
        amberPaint.alpha = 255
    }

    private fun drawCentered(canvas: Canvas, text: String, x: Float, baseline: Float, paint: Paint) {
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, x, baseline, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun ease(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private val easeOutInterpolator = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

    private fun easeOut(value: Float) = easeOutInterpolator.getInterpolation(value.coerceIn(0f, 1f))

    private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction

    // Art is drawn in a fixed 228x400 space that is scaled to the screen, so text must not also
    // follow the user's system font scale (that made the layout drift from the design).
    private fun sp(value: Float) = value

    private companion object {
        const val ART_WIDTH = 228f
        const val ART_HEIGHT = 400f
        const val DURATION_MS = 5900f
        const val COLOR_BEZEL = 0xFF0B0B0B.toInt()
        const val COLOR_SCREEN = 0xFF1E2327.toInt()
        const val COLOR_AMBER = 0xFFFFB020.toInt()
        const val COLOR_GREEN = 0xFF2ECC9A.toInt()
        const val COLOR_MUTED = 0xFF8F9699.toInt()
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        const val COLOR_DESCRIPTION = 0xFFAAB2B8.toInt()
        const val COLOR_HINT = 0xFF7C858B.toInt()
        const val COLOR_OUTLINE = 0xFF4A5258.toInt()
    }
}