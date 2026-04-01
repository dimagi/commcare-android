package org.commcare.views

import android.animation.ValueAnimator
import android.view.View
import androidx.core.animation.doOnEnd

fun View.animateHeight(
    from: Int,
    to: Int,
    duration: Long = 300,
    onEnd: () -> Unit = {},
): ValueAnimator =
    ValueAnimator.ofInt(from, to).apply {
        this.duration = duration
        addUpdateListener {
            layoutParams.height = it.animatedValue as Int
            requestLayout()
        }
        doOnEnd { onEnd() }
        start()
    }
