package org.commcare.utils

import android.app.Activity
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.connect.network.connectId.PersonalIdApiErrorHandler
import org.commcare.connect.network.connectId.PersonalIdApiHandler


class PersonalIdAuthService(
    private val activity: Activity,
    private val personalIdSessionData: PersonalIdSessionData,
    private val callback: OtpVerificationCallback
) : OtpAuthService {

    override fun requestOtp(phoneNumber: String) {
        object : PersonalIdApiHandler<PersonalIdSessionData>() {
            override fun onSuccess(sessionData: PersonalIdSessionData) {
                callback.onCodeSent(null)
            }

            override fun onFailure(failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
                handlePersonalIdApiError(failureCode, t)
            }
        }.sendOtp(activity, personalIdSessionData)
    }

    override fun verifyOtp(code: String) {
        // no verification step just succeed directly
        callback.onCodeVerified(code)
    }

    override fun submitOtp(code: String) {
        object : PersonalIdApiHandler<PersonalIdSessionData>() {
            override fun onSuccess(sessionData: PersonalIdSessionData) {
                callback.onSuccess()
            }

            override fun onFailure(failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
                handlePersonalIdApiError(failureCode, t)
            }
        }.validateOtp(activity, code, personalIdSessionData)
    }

    private fun handlePersonalIdApiError(failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
        var errorType = OtpErrorType.GENERIC_ERROR;
        val error = PersonalIdApiErrorHandler.handle(activity, failureCode, t)
        if(failureCode == PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR) {
            errorType = OtpErrorType.INVALID_CREDENTIAL
        }
        callback.onFailure(errorType, error)
    }
}
