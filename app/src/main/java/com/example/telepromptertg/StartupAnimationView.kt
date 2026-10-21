package com.example.telepromptertg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View

/** Draws the staged startup animation from the supplied HTML reference. */
class StartupAnimationView(context: Context) : View(context) {

    var progress = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val density = resources.displayMetrics.density
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
        canvas.drawColor(COLOR_SCREEN)
        if (width == 0 || height == 0) return
        val scale = width / (ART_WIDTH * density)
        val logicalHeight = height / (scale * density)
        val top = (logicalHeight - ART_HEIGHT) / 2f
        canvas.save()
        canvas.scale(scale * density, scale * density)
        canvas.translate(0f, top)
        drawScreen(canvas, progress * DURATION_MS)
        canvas.restore()
    }

    private fun drawScreen(canvas: Canvas, time: Float) {
        drawOpeningMark(canvas, time)
        drawScriptLines(canvas, time)
        drawReadingMarkers(canvas, time)
        drawSettledHeader(canvas, time)
        drawAppContent(canvas, time)
    }

    private fun drawOpeningMark(canvas: Canvas, time: Float) {
        val drawProgress = ease((time - 60f) / 600f)

        repeat(4) { index ->
            val start = 500f + index * 150f
            val appear = ease((time - start) / 350f) * (1f - ease((time - 2200f) / 320f))
            if (appear <= 0f) return@repeat
            val y = 180f + index * 16f - (1f - appear) * 16f
            val opacity = floatArrayOf(0.9f, 0.65f, 0.45f, 0.25f)[index]
            whitePaint.alpha = (appear * opacity * 255).toInt()
            canvas.drawRoundRect(102f, y, 126f, y + 8f, 4f, 4f, whitePaint)
        }
        whitePaint.alpha = 255

        val settle = ease((time - 2200f) / 700f)
        val titleSettle = ease((time - 3900f) / 500f)
        val startX = lerp(-156f, 64f, drawProgress)
        val scriptX = lerp(64f, 39f, settle)
        val headerX = lerp(39f, 59f, titleSettle)
        val x = lerp(scriptX, headerX, titleSettle)
        val y = lerp(lerp(150f, 192f, settle), 60f, titleSettle)
        val width = lerp(lerp(100f, 150f, settle), 110f, titleSettle)
        val height = lerp(lerp(16f, 16f, settle), 4f, titleSettle)
        val alpha = (drawProgress * 255).toInt()
        if (time < 2200f) {
            amberPaint.alpha = alpha
            canvas.drawRoundRect(startX, 150f, startX + 100f, 166f, 8f, 8f, amberPaint)
        } else {
            amberPaint.alpha = 255
            canvas.drawRoundRect(x, y, x + width, y + height, height / 2f, height / 2f, amberPaint)
        }
        amberPaint.alpha = 255

        val pulse = ((time - 1400f) / 800f).coerceIn(0f, 1f)
        if (pulse > 0f && pulse < 1f) {
            ringPaint.alpha = ((1f - pulse) * 230f).toInt()
            val radius = lerp(27.5f, 104.5f, ease(pulse))
            canvas.drawCircle(114f, 158f, radius, ringPaint)
            ringPaint.alpha = 255
        }
    }

    private fun drawScriptLines(canvas: Canvas, time: Float) {
        val reveal = ease((time - 2200f) / 900f)
        if (reveal <= 0f) return

        val fade = 1f - ease((time - 3900f) / 300f)
        val lineY = floatArrayOf(-89f, -61f, -33f, 23f, 51f, 79f)
        val lineWidth = floatArrayOf(56f, 70f, 104f, 104f, 70f, 56f)
        val alpha = intArrayOf(38, 77, 153, 153, 77, 38)
        lineY.forEachIndexed { index, offset ->
            val y = 200f + offset + (1f - reveal) * 170f
            whitePaint.alpha = (alpha[index] * fade).toInt()
            val barWidth = lineWidth[index] * reveal
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
        val grow = ease((time - 2800f) / 450f)
        val fade = 1f - ease((time - 3900f) / 300f)
        val alpha = (grow * fade * 255).toInt()
        if (alpha == 0) return

        val travel = (1f - grow) * 60f
        val markerY = 191f
        amberPaint.alpha = alpha
        val left = 20f - travel
        val right = 208f + travel
        val markerHeight = 18f * grow
        val markerWidth = 13f * grow
        val halfHeight = markerHeight / 2f

        val leftMarker = android.graphics.Path().apply {
            moveTo(left, markerY)
            lineTo(left - markerWidth, markerY - halfHeight)
            lineTo(left - markerWidth, markerY + halfHeight)
            close()
        }
        canvas.drawPath(leftMarker, amberPaint)
        val rightMarker = android.graphics.Path().apply {
            moveTo(right, markerY)
            lineTo(right + markerWidth, markerY - halfHeight)
            lineTo(right + markerWidth, markerY + halfHeight)
            close()
        }
        canvas.drawPath(rightMarker, amberPaint)
        amberPaint.alpha = 255
    }

    private fun drawSettledHeader(canvas: Canvas, time: Float) {
        val settle = ease((time - 3900f) / 500f)
        if (settle <= 0f) return

        val titleAlpha = ease((time - 4300f) / 450f)
        if (titleAlpha > 0f) {
            titlePaint.alpha = (titleAlpha * 255).toInt()
            canvas.drawText("Teleprompter", 114f, 43f, titlePaint)
            titlePaint.alpha = 255
        }
    }

    private fun drawAppContent(canvas: Canvas, time: Float) {
        val reveal = ease((time - 4800f) / 450f)
        if (reveal <= 0f) return

        descriptionPaint.alpha = (reveal * 255).toInt()
        canvas.drawText("Read your script while recording", 16f, 96f, descriptionPaint)
        canvas.drawText("videos with your camera app.", 16f, 109f, descriptionPaint)

        val editorAlpha = ease((time - 4950f) / 450f)
        if (editorAlpha > 0f) {
            outlinePaint.alpha = (editorAlpha * 255).toInt()
            backgroundPaint.alpha = (editorAlpha * 20).toInt()
            canvas.drawRoundRect(RectF(16f, 124f, 212f, 244f), 10f, 10f, backgroundPaint)
            canvas.drawRoundRect(RectF(16f, 124f, 212f, 244f), 10f, 10f, outlinePaint)
            backgroundPaint.alpha = 255
            hintPaint.alpha = (editorAlpha * 255).toInt()
            canvas.drawText("Enter or paste your text / script", 26f, 143f, hintPaint)
            canvas.drawText("here...", 26f, 154f, hintPaint)
            hintPaint.alpha = 255
        }
        outlinePaint.alpha = 255

        val buttonReveal = ease((time - 5100f) / 450f)
        if (buttonReveal > 0f) {
            amberPaint.color = COLOR_GREEN
            amberPaint.alpha = (buttonReveal * 255).toInt()
            canvas.drawRoundRect(RectF(16f, 262f, 212f, 302f), 10f, 10f, amberPaint)
            buttonTextPaint.alpha = (buttonReveal * 255).toInt()
            val baseline = 287f
            drawCentered(canvas, "Start Teleprompter", 114f, baseline, buttonTextPaint)
            amberPaint.color = COLOR_AMBER
            amberPaint.alpha = 255
            buttonTextPaint.alpha = 255
        }

        val navReveal = ease((time - 5250f) / 450f)
        if (navReveal > 0f) drawNavigation(canvas, navReveal)
        descriptionPaint.alpha = 255
    }

    private fun drawNavigation(canvas: Canvas, reveal: Float) {
        val y = 352f
        val height = 36f
        val navPaint = paint(COLOR_WHITE).apply { textSize = sp(9f); alpha = (reveal * 255).toInt() }
        amberPaint.color = COLOR_GREEN
        amberPaint.alpha = (reveal * 255).toInt()
        canvas.drawRoundRect(RectF(12f, y, 76f, y + height), 12f, 12f, amberPaint)
        buttonTextPaint.textSize = sp(9f)
        buttonTextPaint.alpha = (reveal * 255).toInt()
        drawCentered(canvas, "Script", 44f, y + 22f, buttonTextPaint)
        drawCentered(canvas, "Settings", 114f, y + 22f, navPaint)
        drawCentered(canvas, "Help", 184f, y + 22f, navPaint)
        amberPaint.color = COLOR_AMBER
        amberPaint.alpha = 255
        buttonTextPaint.textSize = sp(13f)
        buttonTextPaint.alpha = 255
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

    private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics) / density

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
