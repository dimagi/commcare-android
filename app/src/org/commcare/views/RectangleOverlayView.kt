package org.commcare.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import org.commcare.dalvik.R
import org.commcare.utils.getOptionalFloat

/**
 * Rectangular reticle drawn over the camera preview as a framing guide. Its width and height are
 * scaled independently as fractions of the screen's width and height, per orientation. Does not
 * crop or alter the captured frame.
 */
class RectangleOverlayView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        private var reticleRect: RectF? = null

        private var configuredWidthFractionPortrait: Float? = null
        private var configuredHeightFractionPortrait: Float? = null
        private var configuredWidthFractionLandscape: Float? = null
        private var configuredHeightFractionLandscape: Float? = null

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
                configuredWidthFractionPortrait = getOptionalFloat(R.styleable.RectangleOverlayView_reticleWidthFractionPortrait)
                configuredHeightFractionPortrait = getOptionalFloat(R.styleable.RectangleOverlayView_reticleHeightFractionPortrait)
                configuredWidthFractionLandscape = getOptionalFloat(R.styleable.RectangleOverlayView_reticleWidthFractionLandscape)
                configuredHeightFractionLandscape = getOptionalFloat(R.styleable.RectangleOverlayView_reticleHeightFractionLandscape)
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
                reticleRect = computeReticleRect(w, h)
            }
        }

        /**
         * Centered reticle for a [w] x [h] view. Width scales off [w] and height off [h] using the
         * configured fractions, each falling back to the orientation default when unset and clamped
         * up to the orientation minimum.
         */
        private fun computeReticleRect(
            w: Int,
            h: Int,
        ): RectF {
            val landscape = w > h
            val widthFraction =
                resolveFraction(
                    if (landscape) configuredWidthFractionLandscape else configuredWidthFractionPortrait,
                    if (landscape) LANDSCAPE_WIDTH_DEFAULT else PORTRAIT_WIDTH_DEFAULT,
                    if (landscape) LANDSCAPE_WIDTH_MIN else PORTRAIT_WIDTH_MIN,
                )
            val heightFraction =
                resolveFraction(
                    if (landscape) configuredHeightFractionLandscape else configuredHeightFractionPortrait,
                    if (landscape) LANDSCAPE_HEIGHT_DEFAULT else PORTRAIT_HEIGHT_DEFAULT,
                    if (landscape) LANDSCAPE_HEIGHT_MIN else PORTRAIT_HEIGHT_MIN,
                )
            val reticleWidth = w * widthFraction
            val reticleHeight = h * heightFraction
            val left = (w - reticleWidth) / 2f
            val top = (h - reticleHeight) / 2f
            return RectF(left, top, left + reticleWidth, top + reticleHeight)
        }

        private fun resolveFraction(
            configured: Float?,
            default: Float,
            min: Float,
        ): Float = (configured ?: default).coerceAtLeast(min)

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
            private const val PORTRAIT_WIDTH_DEFAULT = 0.90f
            private const val PORTRAIT_WIDTH_MIN = 0.80f
            private const val PORTRAIT_HEIGHT_DEFAULT = 0.25f
            private const val PORTRAIT_HEIGHT_MIN = 0.20f
            private const val LANDSCAPE_WIDTH_DEFAULT = 0.70f
            private const val LANDSCAPE_WIDTH_MIN = 0.60f
            private const val LANDSCAPE_HEIGHT_DEFAULT = 0.50f
            private const val LANDSCAPE_HEIGHT_MIN = 0.40f
        }
    }
