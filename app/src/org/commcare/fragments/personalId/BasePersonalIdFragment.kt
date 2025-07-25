package org.commcare.fragments.personalId

import androidx.fragment.app.Fragment
import org.commcare.connect.ConnectConstants
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.dalvik.R
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil

abstract class BasePersonalIdFragment : Fragment() {

    fun handleCommonSignupFailures(
        failureCode: PersonalIdOrConnectApiErrorCodes
    ): Boolean {
        return when (failureCode) {
            PersonalIdOrConnectApiErrorCodes.ACCOUNT_LOCKED_ERROR -> {
                onConfigurationFailure(
                    AnalyticsParamValue.START_CONFIGURATION_LOCKED_ACCOUNT_FAILURE,
                    getString(R.string.personalid_configuration_locked_account)
                )
                true
            }
            else -> {
                false
            }
        }
    }

    protected fun onConfigurationFailure(failureCause: String, errorMessage: String) {
        FirebaseAnalyticsUtil.reportPersonalIdConfigurationFailure(failureCause)
        navigateToMessageDisplay(
            getString(R.string.personalid_configuration_process_failed_title),
            errorMessage,
            false,
            ConnectConstants.PERSONALID_DEVICE_CONFIGURATION_FAILED,
            R.string.ok
        )
    }

    protected abstract fun navigateToMessageDisplay(
        title: String,
        message: String?,
        isCancellable: Boolean,
        phase: Int,
        buttonText: Int
    )
}
