package org.commcare.fragments.personalId

import android.view.ViewTreeObserver
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import org.commcare.connect.ConnectConstants
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil

abstract class BasePersonalIdFragment : Fragment() {
    fun handleCommonSignupFailures(failureCode: PersonalIdOrConnectApiErrorCodes): Boolean =
        when (failureCode) {
            PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR -> {
                onConfigurationFailure(
                    AnalyticsParamValue.START_CONFIGURATION_LOCKED_ACCOUNT_FAILURE,
                    getString(R.string.personalid_configuration_locked_account),
                )
                true
            }

            else -> {
                false
            }
        }

    protected fun onConfigurationFailure(
        failureCause: String,
        errorMessage: String,
    ) {
        FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(failureCause)
        navigateToMessageDisplay(
            getString(R.string.personalid_configuration_process_failed_title),
            errorMessage,
            false,
            ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED,
            R.string.ok,
        )
    }

    protected open fun getNavController(): NavController = NavHostFragment.findNavController(this)

    protected abstract fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int,
    )

    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var lastVisibleHeight = 0

    protected fun setupKeyboardScrollListener(scrollView: ScrollView) {
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val visibleHeight = scrollView.height
            if (lastVisibleHeight > 0 && visibleHeight < lastVisibleHeight) {
                val contentHeight = scrollView.getChildAt(0)?.bottom ?: 0
                scrollView.smoothScrollTo(0, contentHeight)
            }
            lastVisibleHeight = visibleHeight
        }
        scrollView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    protected fun destroyKeyboardScrollListener(scrollView: ScrollView) {
        globalLayoutListener?.let { listener ->
            if (scrollView.viewTreeObserver.isAlive) {
                scrollView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        }
        globalLayoutListener = null
        lastVisibleHeight = 0
    }

}
