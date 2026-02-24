package org.commcare.fragments.base

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.animation.doOnEnd
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import org.commcare.dalvik.databinding.InlineErrorLayoutBinding
import org.commcare.dalvik.databinding.LoadingBinding
import org.commcare.interfaces.base.BaseConnectView

abstract class BaseConnectFragment<B : ViewBinding> :
    Fragment(),
    BaseConnectView {
    private var _binding: B? = null
    val binding get() = _binding!!

    private lateinit var loadingBinding: LoadingBinding
    private lateinit var errorBinding: InlineErrorLayoutBinding
    private lateinit var rootView: View
    val delayDurationMs: Long = 5000

    /**
     * Implement this method in child fragments to inflate their specific binding.
     */
    protected abstract fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): B

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // Inflate child fragment's main view
        _binding = inflateBinding(inflater, container)
        val mainView = binding.root

        // Inflate loading layout
        loadingBinding = LoadingBinding.inflate(inflater, container, false)
        val loadingView = loadingBinding.root

        errorBinding = InlineErrorLayoutBinding.inflate(inflater, container, false)
        val errorView = errorBinding.root
        errorView.visibility = View.GONE

        val rootFrame =
            FrameLayout(requireContext()).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        val verticalContainer =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        verticalContainer.addView(errorView)
        verticalContainer.addView(mainView)

        rootFrame.addView(verticalContainer)
        rootFrame.addView(loadingView)

        rootView = rootFrame

        hideLoading()
        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideError()
        _binding = null
        // No need to nullify loadingBinding since it's lateinit — but safe practice to hide it
        loadingBinding.root.visibility = View.GONE
    }

    override fun showLoading() {
        loadingBinding.root.visibility = View.VISIBLE
    }

    override fun hideLoading() {
        loadingBinding.root.visibility = View.GONE
    }

    fun showError(error: String?) {
        if (error == null) return

        val errorView = errorBinding.root
        errorBinding.tvErrorMessage.text = error
        errorView.removeCallbacks(hideErrorRunnable)

        errorView.measure(
            View.MeasureSpec.makeMeasureSpec(
                (errorView.parent as View).width,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.UNSPECIFIED
        )

        val targetHeight = errorView.measuredHeight

        errorView.layoutParams.height = 0
        errorView.visibility = View.VISIBLE

        val animator = ValueAnimator.ofInt(0, targetHeight)
        animator.duration = 300
        animator.addUpdateListener {
            errorView.layoutParams.height = it.animatedValue as Int
            errorView.requestLayout()
        }

        animator.doOnEnd {
            errorView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        animator.start()

        errorView.postDelayed(hideErrorRunnable, delayDurationMs)
    }

    fun hideError() {
        val errorView = errorBinding.root
        errorView.removeCallbacks(hideErrorRunnable)

        val initialHeight = errorView.height
        if (initialHeight == 0) {
            errorView.visibility = View.GONE
            return
        }

        val animator = ValueAnimator.ofInt(initialHeight, 0)
        animator.duration = 300
        animator.addUpdateListener {
            errorView.layoutParams.height = it.animatedValue as Int
            errorView.requestLayout()
        }

        animator.doOnEnd {
            errorView.visibility = View.GONE
            errorView.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        animator.start()
    }

    private val hideErrorRunnable =
        Runnable {
            hideError()
        }
}
