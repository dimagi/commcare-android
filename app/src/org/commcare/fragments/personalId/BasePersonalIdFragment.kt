package org.commcare.fragments.personalId

import androidx.fragment.app.Fragment
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.dalvik.R;

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

    protected abstract fun onConfigurationFailure(failureCode: String, errorMessage: String)

}
