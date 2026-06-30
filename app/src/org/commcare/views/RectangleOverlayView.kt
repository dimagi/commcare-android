package org.commcare.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
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

        private val scrimPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = ContextCompat.getColor(context, R.color.black_60)
            }

        private val strokeWidthPx = resources.getDimension(R.dimen.reticle_stroke_width)
        private val outlineWidthPx = resources.getDimension(R.dimen.reticle_outline_width)

        private val outlinePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = ContextCompat.getColor(context, R.color.black_80)
                strokeWidth = strokeWidthPx + 2 * outlineWidthPx
            }

        private val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = ContextCompat.getColor(context, R.color.white)
                strokeWidth = strokeWidthPx
            }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                val margin = RETICLE_MARGIN_RATIO * w
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
            const val RETICLE_MARGIN_RATIO = 0.08f
        }
    }
