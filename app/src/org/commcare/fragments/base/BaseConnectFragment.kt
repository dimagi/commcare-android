package org.commcare.fragments.base

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import org.commcare.AppUtils
import org.commcare.activities.CommCareVerificationActivity
import org.commcare.activities.connect.ConnectActivity
import org.commcare.android.database.connect.models.ConnectJobRecord
import org.commcare.connect.ConnectAppLaunchController
import org.commcare.connect.network.base.BaseApiHandler
import org.commcare.connect.network.personalId.TokenExceptionHandler.handleTokenDeniedException
import org.commcare.connect.repository.ConnectSyncPreferences
import org.commcare.connect.repository.DataState
import org.commcare.connect.viewmodel.AppInstallFailureRecovery
import org.commcare.connect.viewmodel.AppInstallState
import org.commcare.connect.viewmodel.AppInstallTarget
import org.commcare.connect.viewmodel.ConnectAppInstallViewModel
import org.commcare.dalvik.R
import org.commcare.dalvik.databinding.LoadingBinding
import org.commcare.dalvik.databinding.NetworkStatusBarLayoutBinding
import org.commcare.engine.resource.ResourceInstallUtils
import org.commcare.fragments.RefreshableFragment
import org.commcare.interfaces.base.BaseConnectView
import org.commcare.utils.ConnectivityStatus
import org.commcare.views.NetworkStatusBarViewController
import org.commcare.views.dialogs.CustomProgressDialog
import org.javarosa.core.services.Logger
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
    private lateinit var errorBinding: NetworkStatusBarLayoutBinding
    private lateinit var rootView: View
    private var mNetworkStatusBarViewController: NetworkStatusBarViewController? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastDataState: DataState<*>? = null
    private var wasOffline: Boolean = false

    private var installDialog: CustomProgressDialog? = null

    /**
     * Shared with every other Connect screen in this activity, since only one app installs at a
     * time. [installOwnerKey] is what distinguishes the screen driving the install from the ones
     * merely watching it.
     */
    private val installViewModel: ConnectAppInstallViewModel by lazy {
        ViewModelProvider(
            requireActivity(),
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application),
        )[ConnectAppInstallViewModel::class.java]
    }

    /** Stable across recreation, so a screen reclaims the install it started before a rotation. */
    private val installOwnerKey get() = javaClass.name

    private val ownedAppInstallTarget: AppInstallTarget?
        get() = installViewModel.target?.takeIf { it.ownerKey == installOwnerKey }

    /** True while the install in flight is one this screen started, rather than another screen's. */
    protected val ownsAppInstall get() = ownedAppInstallTarget != null

    private val verificationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                ownedAppInstallTarget?.let {
                    // Dispatched before the launch so a dialog-based screen closes its download
                    // dialog rather than flashing it behind the launch dialog.
                    installViewModel.markLaunching()
                    launchInstalledApp(it)
                }
            } else {
                // A canceled verification is a back-press, not a failure; the restored CTA is the retry.
                installViewModel.clear()
            }
        }

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

    /** Loading bar this fragment drives. Override to use one in the fragment's own layout. */
    protected open val loadingBarViewId: Int = R.id.include_network_loading

    /**
     * Container in the fragment's layout to host the status bar. Override to place the bar there
     * instead of directly above the fragment's content.
     */
    protected open val statusBarContainerViewId: Int = View.NO_ID

    fun getLastSyncTime(): Date? {
        val endpoint = getEndpoint() ?: return null
        return ConnectSyncPreferences.getInstance().getLastSyncTime(endpoint)
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

        verticalContainer.addView(
            mainView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        rootFrame.addView(verticalContainer)

        // The fragment's own view is searched first because it is not attached to the activity yet,
        // so a bar the fragment owns is invisible to the activity-wide lookup at this point.
        progressBar = mainView.findViewById(loadingBarViewId)
            ?: requireActivity().findViewById(loadingBarViewId)
            ?: run {
                val loadingBinding = LoadingBinding.inflate(inflater, container, false)
                rootFrame.addView(loadingBinding.root)
                loadingBinding.progressBar
            }
        hideLoading()

        errorBinding = NetworkStatusBarLayoutBinding.inflate(inflater, container, false)
        val errorView = errorBinding.root
        errorView.visibility = View.GONE
        mNetworkStatusBarViewController = NetworkStatusBarViewController(errorBinding)

        // findViewById answers null for NO_ID, so the default lands on the fallback unaided.
        val statusBarContainer = mainView.findViewById<ViewGroup>(statusBarContainerViewId)
        if (statusBarContainer != null) {
            statusBarContainer.addView(errorView)
        } else {
            verticalContainer.addView(errorView, 0)
        }

        rootView = rootFrame
        return rootView
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        installDialog = childFragmentManager.findFragmentByTag(INSTALL_DIALOG_TAG) as? CustomProgressDialog
        installViewModel.installState.observe(viewLifecycleOwner) { onAppInstallState(it) }
    }

    override fun onStart() {
        super.onStart()
        if (shouldMonitorNetwork()) {
            registerNetworkCallback()
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkCallback()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mNetworkStatusBarViewController!!.cleanup()
        mNetworkStatusBarViewController = null
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
        mNetworkStatusBarViewController!!.showError(error)
    }

    fun isErrorShowing(): Boolean = lastDataState is DataState.Error

    fun hideError() {
        mNetworkStatusBarViewController!!.hide()
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

                is DataState.Cached -> {
                    onCached.accept(state.data)
                }

                is DataState.Success -> {
                    hideLoading()
                    hideError()
                    if (wasOffline) {
                        showBackOnline()
                    } else {
                        showSyncSuccess()
                    }
                    wasOffline = false
                    onSuccess.accept(state.data)
                }

                is DataState.Error -> {
                    hideLoading()
                    if (state.errorCode == BaseApiHandler.PersonalIdOrConnectApiErrorCodes.TOKEN_DENIED_ERROR) {
                        handleTokenDeniedException()
                    } else if (state.isNetworkError() &&
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
        mNetworkStatusBarViewController!!.showMessage(getString(R.string.connect_sync_successful))
    }

    private fun showBackOnline() {
        mNetworkStatusBarViewController!!.showBackOnline(getString(R.string.connect_sync_successful))
    }

    private fun shouldMonitorNetwork(): Boolean = this is RefreshableFragment

    private fun showOfflineIndicator() {
        wasOffline = true
        val relativeTime = getRelativeLastSyncTime()
        val message = getString(R.string.connect_last_synced, relativeTime)
        mNetworkStatusBarViewController!!.showOfflineStatus(message)
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
        val activity = getActivity()
        if (activity is ConnectActivity) {
            activity.setWaitDialogEnabled(enabled)
        }
    }

    /**
     * Opens an opportunity's learn or delivery app, downloading and verifying it first when the
     * device does not have it yet. The user stays on this screen throughout; progress and failures
     * are rendered by [onAppInstallStateChanged].
     */
    protected fun launchApp(
        job: ConnectJobRecord,
        isLearning: Boolean,
    ) {
        val app = if (isLearning) job.learnAppInfo else job.deliveryAppInfo
        val target = AppInstallTarget(app.appId, isLearning, installOwnerKey)

        if (AppUtils.isAppInstalled(app.appId)) {
            launchInstalledApp(target)
            return
        }
        if (!installViewModel.install(target, app.installUrl)) {
            // Another screen's install is already running; it stays the one being reported.
            Logger.exception(
                "Ignored a Connect app launch for ${app.appId}",
                Exception("An install is already running"),
            )
        }
    }

    /**
     * Renders [state] for this screen. The default is a blocking progress dialog and a toast on
     * failure, which suits screens with no action bar of their own; screens that own a
     * [org.commcare.views.connect.ConnectCtaBar] render into it instead.
     */
    protected open fun onAppInstallStateChanged(
        state: AppInstallState,
        isLearning: Boolean,
    ) {
        when (state) {
            is AppInstallState.Downloading ->
                showInstallDialog(isLearning, state.percent, state.status)

            AppInstallState.Installed, AppInstallState.Verifying ->
                showInstallDialog(
                    isLearning,
                    INSTALL_PROGRESS_MAX,
                    getString(R.string.connect_app_finishing_install),
                )

            is AppInstallState.Failed -> {
                dismissInstallDialog()
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
            }

            AppInstallState.Launching, AppInstallState.Idle -> dismissInstallDialog()
        }
    }

    /**
     * Only the screen that started an install reports it or acts on its result, so a second screen
     * observing the same install neither duplicates the launch nor shows a stray dialog.
     */
    private fun onAppInstallState(state: AppInstallState) {
        // Re-applied on every state so a screen recreated mid-install locks itself down again, and
        // any screen re-enables the action bar once the install that disabled it is over.
        setBackButtonAndActionBarState(!installViewModel.isInstalling)

        ownedAppInstallTarget?.let { target ->
            onAppInstallStateChanged(state, target.isLearning)

            when (state) {
                AppInstallState.Installed -> startAppVerification()
                is AppInstallState.Failed ->
                    if (installViewModel.consumeFailure()) {
                        recoverFromInstallFailure(state)
                    }

                else -> Unit
            }
            return
        }
        onAppInstallStateChanged(AppInstallState.Idle, false)
    }

    /** An installed app still needs its media verified before it can be seated and launched. */
    private fun startAppVerification() {
        installViewModel.markVerifying()
        Toast.makeText(requireContext(), R.string.connect_app_installed, Toast.LENGTH_SHORT).show()
        verificationLauncher.launch(
            Intent(requireContext(), CommCareVerificationActivity::class.java)
                .putExtra(CommCareVerificationActivity.KEY_LAUNCH_FROM_CONNECT, true),
        )
    }

    /**
     * Holds the busy state until the app is actually open, so the bar does not revert to its CTA
     * while the launch dialog is still working through seating, sign-in and sync.
     */
    private fun launchInstalledApp(target: AppInstallTarget) {
        ConnectAppLaunchController(this).launchApp(
            target.appId,
            target.isLearning,
            { installViewModel.clear() },
            { installViewModel.clear() },
        )
    }

    /** The two install failures the user can only act on through a prompt of their own. */
    private fun recoverFromInstallFailure(state: AppInstallState.Failed) {
        when (val recovery = state.recovery) {
            is AppInstallFailureRecovery.CommCareApkUpdate ->
                ResourceInstallUtils.showApkUpdatePrompt(
                    activity,
                    recovery.versionRequired,
                    recovery.versionAvailable,
                )

            AppInstallFailureRecovery.TargetMismatch -> ResourceInstallUtils.showTargetMismatchError(activity)
            AppInstallFailureRecovery.None -> Unit
        }
    }

    private fun showInstallDialog(
        isLearning: Boolean,
        percent: Int,
        status: String?,
    ) {
        if (installDialog == null) {
            // Showing a dialog after the fragment has saved its state would throw, so skip it.
            if (childFragmentManager.isStateSaved) {
                return
            }
            installDialog =
                CustomProgressDialog
                    .newInstance(
                        getString(downloadingMessage(isLearning)),
                        status ?: getString(R.string.connect_app_install_setting_up),
                        INSTALL_DIALOG_TASK_ID,
                    ).apply { addProgressBar() }
            installDialog?.showNow(childFragmentManager, INSTALL_DIALOG_TAG)
        }
        installDialog?.updateMessage(status ?: getString(R.string.connect_app_install_setting_up))
        installDialog?.updateProgressBar(percent, INSTALL_PROGRESS_MAX)
    }

    private fun dismissInstallDialog() {
        installDialog?.let {
            if (it.isAdded) {
                it.dismissAllowingStateLoss()
            }
        }
        installDialog = null
    }

    private fun setBackButtonAndActionBarState(enabled: Boolean) {
        (activity as? ConnectActivity)?.setBackButtonAndActionBarState(enabled)
    }

    @StringRes
    protected fun downloadingMessage(isLearning: Boolean): Int =
        if (isLearning) R.string.connect_downloading_learn else R.string.connect_downloading_delivery

    /** True while any Connect screen in this activity has an app install in flight. */
    protected val isInstallingApp get() = installViewModel.isInstalling

    /** Drops a failure the user has dismissed, so re-rendering the screen does not bring it back. */
    protected fun forgetInstallFailure() {
        if (installViewModel.installState.value is AppInstallState.Failed) {
            installViewModel.clear()
        }
    }

    companion object {
        private const val INSTALL_DIALOG_TAG = "connect_install_progress"

        // Negative so it can't collide with the positive task ids CommCareActivity assigns to real tasks.
        private const val INSTALL_DIALOG_TASK_ID = -11
        private const val INSTALL_PROGRESS_MAX = 100
    }
}
