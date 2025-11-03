package org.commcare.fragments.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import org.commcare.dalvik.databinding.LoadingBinding
import org.commcare.interfaces.base.BaseConnectView

abstract class BaseConnectFragment<B : ViewBinding> :
    Fragment(),
    BaseConnectView {
    private var _binding: B? = null
    val binding get() = _binding!!

    private lateinit var loadingBinding: LoadingBinding
    private lateinit var rootView: View

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

        // Create a parent container that holds both mainView and loadingView
        val mergedLayout =
            FrameLayout(
                requireContext(),
            ).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                addView(mainView)
                addView(loadingView)
            }

        // Hide loading by default
        hideLoading()

        rootView = mergedLayout
        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
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
}
