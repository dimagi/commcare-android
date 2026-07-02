package org.commcare.views.connect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import org.commcare.dalvik.R

/**
 * Draws a 180° semi-circle: a full track arc with a [progress] arc painted over it, sweeping
 * clockwise from the left of the circle, over the top, to the right.
 *
 * This view only renders the arc; text is overlaid by [SemiCircleProgressBar], which owns it.
 */
class SemiCircleArcView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private val trackPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
        private val progressPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
        private val arcBounds = RectF()

        var strokeWidth: Float = resources.getDimension(R.dimen.semi_circle_progress_stroke_width)
            set(value) {
                field = value
                trackPaint.strokeWidth = value
                progressPaint.strokeWidth = value
                requestLayout()
                invalidate()
            }

        var trackColor: Int = Color.LTGRAY
            set(value) {
                field = value
                trackPaint.color = value
                invalidate()
            }

        var progressColor: Int = Color.BLUE
            set(value) {
                field = value
                progressPaint.color = value
                invalidate()
            }

        /** Fraction filled, in the range 0..1. */
        var progress: Float = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }

        init {
            trackPaint.strokeWidth = strokeWidth
            progressPaint.strokeWidth = strokeWidth
            trackPaint.color = trackColor
            progressPaint.color = progressColor
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val defaultWidth = resources.getDimension(R.dimen.semi_circle_progress_default_width).toInt()
            val width =
                when (MeasureSpec.getMode(widthMeasureSpec)) {
                    MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)

                    // wrap_content: use the intrinsic default, but never exceed the offered space.
                    MeasureSpec.AT_MOST -> minOf(defaultWidth, MeasureSpec.getSize(widthMeasureSpec))

                    else -> defaultWidth
                }
            val radius = (width - paddingLeft - paddingRight - strokeWidth) / 2f
            // Top half of the circle plus the rounded caps and padding.
            val height = (paddingTop + paddingBottom + strokeWidth + radius).toInt()
            setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val contentWidth = width - paddingLeft - paddingRight
            val radius = (contentWidth - strokeWidth) / 2f
            val centerX = paddingLeft + contentWidth / 2f
            val top = paddingTop + strokeWidth / 2f
            arcBounds.set(centerX - radius, top, centerX + radius, top + 2 * radius)

            canvas.drawArc(arcBounds, START_ANGLE, SWEEP_ANGLE, false, trackPaint)
            if (progress > 0f) {
                canvas.drawArc(arcBounds, START_ANGLE, SWEEP_ANGLE * progress, false, progressPaint)
            }
        }

        companion object {
            // Sweep clockwise from the left of the circle, over the top, to the right.
            private const val START_ANGLE = 180f
            private const val SWEEP_ANGLE = 180f
        }
    }
