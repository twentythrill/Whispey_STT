package com.whispey.stt

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min

class CircleRecordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    enum class State {
        Idle,
        Recording,
        Processing
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulseScale = 1f
    private var animator: ValueAnimator? = null
    private var state = State.Idle

    init {
        isClickable = true
        isFocusable = true
        setState(State.Idle)
    }

    fun setState(next: State) {
        state = next
        when (next) {
            State.Recording -> startPulse()
            else -> stopPulse()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = dp(132)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = when (state) {
            State.Idle -> WhispeyTheme.circleIdle(context)
            State.Recording -> WhispeyTheme.circleRecording(context)
            State.Processing -> WhispeyTheme.circleProcessing(context)
        }
        val radius = min(width, height) * 0.5f * pulseScale
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
    }

    private fun startPulse() {
        if (animator?.isStarted == true) return
        animator = ValueAnimator.ofFloat(0.94f, 1f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopPulse() {
        animator?.cancel()
        animator = null
        pulseScale = 1f
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
