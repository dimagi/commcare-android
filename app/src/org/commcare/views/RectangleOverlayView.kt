package org.commcare.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import org.commcare.dalvik.R

/**
 * Responsive rectangular reticle drawn over a camera preview to help the user
 * center the subject. The reticle is a centered rectangle inset by an equal margin on
 * all sides, recomputed from the view size; it does not crop or alter the captured frame.
 */
class RectangleOverlayView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private var reticleRect: RectF? = null

        private var reticleMarginRatio = DEFAULT_RETICLE_MARGIN_RATIO

        private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        init {
            context.withStyledAttributes(
                attrs,
                R.styleable.RectangleOverlayView,
                defStyleAttr,
                R.style.Widget_CommCare_RectangleOverlayView,
            ) {
                reticleMarginRatio =
                    getFloat(
                        R.styleable.RectangleOverlayView_reticleMarginRatio,
                        DEFAULT_RETICLE_MARGIN_RATIO,
                    )
                val strokeWidthPx = getDimension(R.styleable.RectangleOverlayView_reticleStrokeWidth, 0f)
                val outlineWidthPx = getDimension(R.styleable.RectangleOverlayView_reticleOutlineWidth, 0f)

                scrimPaint.color = getColor(R.styleable.RectangleOverlayView_reticleScrimColor, 0)
                outlinePaint.color = getColor(R.styleable.RectangleOverlayView_reticleOutlineColor, 0)
                outlinePaint.strokeWidth = strokeWidthPx + 2 * outlineWidthPx
                strokePaint.color = getColor(R.styleable.RectangleOverlayView_reticleStrokeColor, 0)
                strokePaint.strokeWidth = strokeWidthPx
            }
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                val margin = reticleMarginRatio * minOf(w, h)
                reticleRect = RectF(margin, margin, w - margin, h - margin)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val rect = reticleRect ?: return
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()

            canvas.drawRect(0f, 0f, viewWidth, rect.top, scrimPaint)
            canvas.drawRect(0f, rect.bottom, viewWidth, viewHeight, scrimPaint)
            canvas.drawRect(0f, rect.top, rect.left, rect.bottom, scrimPaint)
            canvas.drawRect(rect.right, rect.top, viewWidth, rect.bottom, scrimPaint)

            canvas.drawRect(rect, outlinePaint)
            canvas.drawRect(rect, strokePaint)
        }

        companion object {
            const val DEFAULT_RETICLE_MARGIN_RATIO = 0.08f
        }
    }
