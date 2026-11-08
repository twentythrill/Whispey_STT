package com.whispey.stt

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * A strictly monochrome, high-contrast toggle switch designed for dark mode surfaces.
 *
 * Features an explicit 1.5dp border that guarantees the pill bounds are always distinct
 * against near-black backgrounds, with zero Material blue or color tinting:
 * - OFF (Local Orukeet): Dark charcoal (#1E1E1E) track, light gray (#777777) border, medium-light (#B0B0B0) thumb.
 * - ON (Cloud Whisper): High-contrast light gray (#E0E0E0) track, pure white (#FFFFFF) border, dark (#121212) thumb.
 */
class MonochromeToggleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpF(1.5f)
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val trackRect = RectF()
    private val argbEvaluator = ArgbEvaluator()

    private var onCheckedChangeListener: ((Boolean) -> Unit)? = null
    private var thumbProgress: Float = 0f
    private var animator: ValueAnimator? = null
    private var isCheckedState: Boolean = false

    var isChecked: Boolean
        get() = isCheckedState
        set(value) {
            if (isCheckedState != value) {
                isCheckedState = value
                animateToState(value)
                onCheckedChangeListener?.invoke(value)
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)
            }
        }

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            isChecked = !isChecked
        }
    }

    fun setCheckedSilently(value: Boolean) {
        isCheckedState = value
        animator?.cancel()
        thumbProgress = if (value) 1f else 0f
        invalidate()
    }

    fun setOnCheckedChangeListener(listener: (Boolean) -> Unit) {
        onCheckedChangeListener = listener
    }

    private fun animateToState(checked: Boolean) {
        animator?.cancel()
        val target = if (checked) 1f else 0f
        animator = ValueAnimator.ofFloat(thumbProgress, target).apply {
            duration = 180L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                thumbProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = dp(WIDTH_DP)
        val height = dp(HEIGHT_DP)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val halfStroke = strokePaint.strokeWidth / 2f
        trackRect.set(
            halfStroke,
            halfStroke,
            width.toFloat() - halfStroke,
            height.toFloat() - halfStroke
        )

        val cornerRadius = trackRect.height() / 2f

        // Interpolate colors based on thumb position
        val currentTrackFill = argbEvaluator.evaluate(
            thumbProgress,
            COLOR_TRACK_FILL_OFF,
            COLOR_TRACK_FILL_ON
        ) as Int

        val currentTrackStroke = argbEvaluator.evaluate(
            thumbProgress,
            COLOR_TRACK_STROKE_OFF,
            COLOR_TRACK_STROKE_ON
        ) as Int

        val currentThumbFill = argbEvaluator.evaluate(
            thumbProgress,
            COLOR_THUMB_FILL_OFF,
            COLOR_THUMB_FILL_ON
        ) as Int

        // 1. Draw track pill fill
        trackPaint.color = currentTrackFill
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        // 2. Draw track pill border (1.5dp high contrast stroke)
        strokePaint.color = currentTrackStroke
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, strokePaint)

        // 3. Draw thumb circle
        val thumbRadius = cornerRadius - dpF(2.5f)
        val startX = trackRect.left + cornerRadius
        val endX = trackRect.right - cornerRadius
        val thumbX = startX + (endX - startX) * thumbProgress
        val thumbY = height / 2f

        thumbPaint.color = currentThumbFill
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dpF(value: Float): Float =
        value * resources.displayMetrics.density

    companion object {
        private const val WIDTH_DP = 46
        private const val HEIGHT_DP = 24

        // OFF State (Local Orukeet)
        private val COLOR_TRACK_FILL_OFF = Color.parseColor("#1E1E1E")
        private val COLOR_TRACK_STROKE_OFF = Color.parseColor("#777777")
        private val COLOR_THUMB_FILL_OFF = Color.parseColor("#B0B0B0")

        // ON State (Cloud Whisper)
        private val COLOR_TRACK_FILL_ON = Color.parseColor("#E0E0E0")
        private val COLOR_TRACK_STROKE_ON = Color.parseColor("#FFFFFF")
        private val COLOR_THUMB_FILL_ON = Color.parseColor("#121212")
    }
}
