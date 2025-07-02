package org.commcare.utils

import android.app.Activity
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.network.PersonalIdApiErrorHandler
import org.commcare.connect.network.PersonalIdApiHandler

class PersonalIdAuthService(
    private val activity: Activity,
    private val personalIdSessionData: PersonalIdSessionData,
    private val callback: OtpVerificationCallback
) : OtpAuthService {

    override fun requestOtp(phoneNumber: String) {
        object : PersonalIdApiHandler() {
            override fun onSuccess(sessionData: PersonalIdSessionData) {
                callback.onCodeSent(null)
            }

            override fun onFailure(failureCode: PersonalIdApiErrorCodes, t: Throwable) {
                handlePersonalIdApiError(failureCode, t)
            }
        }.sendOtp(activity, personalIdSessionData)
    }

    override fun verifyOtp(code: String) {
        // no verification step just succeed directly
        callback.onCodeVerified(code)
    }

    override fun submitOtp(code: String) {
        object : PersonalIdApiHandler() {
            override fun onSuccess(sessionData: PersonalIdSessionData) {
                callback.onSuccess()
            }

            override fun onFailure(failureCode: PersonalIdApiErrorCodes, t: Throwable) {
                handlePersonalIdApiError(failureCode, t)
            }
        }.validateOtp(activity, code, personalIdSessionData)
    }

    private fun handlePersonalIdApiError(failureCode: PersonalIdApiHandler.PersonalIdApiErrorCodes, t: Throwable) {
        val error = PersonalIdApiErrorHandler.handle(activity, failureCode, t)
        callback.onFailure(OtpErrorType.GENERIC_ERROR, error)
    }
}
