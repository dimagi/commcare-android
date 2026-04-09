package org.commcare.fragments.base

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.viewbinding.ViewBinding
import org.commcare.activities.connect.ConnectActivity
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.repository.ConnectSyncPreferences
import org.commcare.connect.repository.DataState
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.InlineErrorLayoutBinding
import org.commcare.dalvik.databinding.LoadingBinding
import org.commcare.fragments.RefreshableFragment
import org.commcare.interfaces.base.BaseConnectView
import org.commcare.utils.ConnectivityStatus
import org.commcare.views.TopBarErrorViewController
import java.util.Date

fun interface DataStateConsumer<T> {
    fun accept(data: T)
}

abstract class BaseConnectFragment<B : ViewBinding> :
    Fragment(),
    BaseConnectView {
    private var _binding: B? = null
    val binding get() = _binding!!

    private lateinit var progressBar: ProgressBar
    private lateinit var errorBinding: InlineErrorLayoutBinding
    private lateinit var rootView: View
    private var topBarErrorViewController: TopBarErrorViewController? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastDataState: DataState<*>? = null

    /**
     * Implement this method in child fragments to inflate their specific binding.
     */
    protected abstract fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): B

    /**
     * Return the API endpoint this fragment syncs from, used to look up the last sync time.
     * Return null if this fragment has no associated endpoint.
     */
    abstract fun getEndpoint(): String?

    fun getLastSyncTime(): Date? {
        val endpoint = getEndpoint() ?: return null
        return ConnectSyncPreferences.getInstance(requireContext()).getLastSyncTime(endpoint)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = inflateBinding(inflater, container)
        val mainView = binding.root

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

        verticalContainer.addView(mainView)
        rootFrame.addView(verticalContainer)

        // Inflate loading layout
        progressBar = requireActivity().findViewById(R.id.include_network_loading)
            ?: run {
                val loadingBinding = LoadingBinding.inflate(inflater, container, false)
                rootFrame.addView(loadingBinding.root)
                loadingBinding.progressBar
            }
        hideLoading()

        errorBinding = InlineErrorLayoutBinding.inflate(inflater, container, false)
        val errorView = errorBinding.root
        errorView.visibility = View.GONE
        topBarErrorViewController = TopBarErrorViewController(errorBinding)
        verticalContainer.addView(errorView, 0)

        rootView = rootFrame
        return rootView
    }

    override fun onStart() {
        super.onStart()
        if (shouldMonitorNetwork() &&
            !ConnectivityStatus.isNetworkAvailable(requireContext())
        ) {
            registerNetworkCallback()
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
        progressBar.visibility = View.GONE
        lastDataState = null
    }

    override fun showLoading() {
        progressBar.visibility = View.VISIBLE
    }

    override fun hideLoading() {
        progressBar.visibility = View.GONE
    }

    fun showError(error: String) {
        topBarErrorViewController!!.showError(error)
    }

    fun isErrorShowing(): Boolean = lastDataState is DataState.Error

    fun hideError() {
        topBarErrorViewController!!.hide()
    }

    /**
     * Observes a LiveData of DataState and handle UI updates for loading, success, cached, and error states.
     */
    protected fun <T> observeDataState(
        liveData: LiveData<DataState<T>>,
        onCached: DataStateConsumer<T>,
        onSuccess: DataStateConsumer<T>,
    ) {
        liveData.observe(viewLifecycleOwner) { state ->
            if (!isAdded) return@observe
            if (lastDataState == null && (state is DataState.Success || state is DataState.Error)) {
                // terminal states should not be shown on initial load to avoid jarring UX
                // this happens when LiveData emits a cached value immediately upon observation
                return@observe
            }
            when (state) {
                is DataState.Loading -> {
                    hideError()
                    showLoading()
                }
                is DataState.Cached -> onCached.accept(state.data)
                is DataState.Success -> {
                    hideLoading()
                    hideError()
                    showSyncSuccess()
                    onSuccess.accept(state.data)
                }
                is DataState.Error -> {
                    hideLoading()
                    if (state.errorCode == BaseApiHandler.PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR &&
                        !ConnectivityStatus.isNetworkAvailable(requireContext())
                    ) {
                        showOfflineIndicator()
                    } else {
                        showError(getString(R.string.connect_sync_failed, getRelativeLastSyncTime()))
                    }
                }
            }
            lastDataState = state
        }
    }

    private fun showSyncSuccess() {
        topBarErrorViewController!!.showMessage(getString(R.string.connect_sync_successful))
    }

    private fun shouldMonitorNetwork(): Boolean = this is RefreshableFragment

    private fun showOfflineIndicator() {
        val relativeTime = getRelativeLastSyncTime()
        val message = getString(R.string.connect_last_synced, relativeTime)
        topBarErrorViewController!!.showOfflineStatus(message)
    }

    private fun getRelativeLastSyncTime(): String {
        val lastSync = getLastSyncTime()
        return if (lastSync != null) {
            DateUtils
                .getRelativeTimeSpanString(
                    lastSync.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString()
        } else {
            getString(R.string.connect_never)
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
                        if (isErrorShowing()) {
                            (this@BaseConnectFragment as RefreshableFragment).refresh(false)
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

    protected fun setWaitDialogEnabled(enabled: Boolean) {
        val activity: Activity? = getActivity()
        if (activity is ConnectActivity) {
            activity.setWaitDialogEnabled(enabled)
        }
    }

}
