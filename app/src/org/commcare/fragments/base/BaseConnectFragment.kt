package org.commcare.fragments.base

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import org.commcare.connect.ConnectDateUtils
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.InlineErrorLayoutBinding
import org.commcare.dalvik.databinding.LoadingBinding
import org.commcare.fragments.RefreshableFragment
import org.commcare.interfaces.base.BaseConnectView
import org.commcare.personalId.PersonalIdFeatureFlagChecker
import org.commcare.utils.ConnectivityStatus
import org.commcare.views.TopBarErrorViewController
import java.util.Date

abstract class BaseConnectFragment<B : ViewBinding> :
    Fragment(),
    BaseConnectView {
    private var _binding: B? = null
    val binding get() = _binding!!

    private lateinit var loadingBinding: LoadingBinding
    private lateinit var errorBinding: InlineErrorLayoutBinding
    private lateinit var rootView: View
    private var topBarErrorViewController: TopBarErrorViewController? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Implement this method in child fragments to inflate their specific binding.
     */
    protected abstract fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): B

    /**
     * Override in subclasses to provide the timestamp of the last successful data sync.
     * Used to display "Last synced: X ago" in the offline indicator.
     */
    open fun getLastSyncTime(): Date? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = inflateBinding(inflater, container)
        val mainView = binding.root

        loadingBinding = LoadingBinding.inflate(inflater, container, false)
        val loadingView = loadingBinding.root

        errorBinding = InlineErrorLayoutBinding.inflate(inflater, container, false)
        val errorView = errorBinding.root
        errorView.visibility = View.GONE
        topBarErrorViewController = TopBarErrorViewController(errorBinding)

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

    override fun onStart() {
        super.onStart()
        if (shouldMonitorNetwork() &&
            !ConnectivityStatus.isNetworkAvailable(requireContext())
        ) {
            registerNetworkCallback()
            showOfflineIndicator()
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkCallback()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        topBarErrorViewController!!.cleanup()
        topBarErrorViewController = null
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

    fun showError(error: String) {
        topBarErrorViewController!!.show(error)
    }

    fun hideError() {
        topBarErrorViewController!!.hide()
    }

    private fun shouldMonitorNetwork(): Boolean =
        this is RefreshableFragment &&
            PersonalIdFeatureFlagChecker.isFeatureEnabled(
                PersonalIdFeatureFlagChecker.FeatureFlag.DATA_REFRESH_INDICATOR,
            )

    private fun showOfflineIndicator() {
        val message = buildOfflineMessage()
        topBarErrorViewController!!.showOfflineStatus(message)
    }

    private fun buildOfflineMessage(): String {
        val lastSync = getLastSyncTime()
        return if (lastSync != null) {
            val relativeTime = ConnectDateUtils.formatNotificationTime(requireContext(), lastSync)
            getString(R.string.connect_last_synced, relativeTime)
        } else {
            getString(R.string.connect_last_synced, getString(R.string.connect_never))
        }
    }

    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val cm =
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return
            connectivityManager = cm

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        view?.post {
                            topBarErrorViewController!!.hide()
                        }
                    }
                }
            networkCallback = callback
            cm.registerDefaultNetworkCallback(callback)
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            connectivityManager?.unregisterNetworkCallback(callback)
        }
        networkCallback = null
        connectivityManager = null
    }
}
