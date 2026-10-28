package org.commcare.utils

import android.app.Activity
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.network.personalId.PersonalIdApiHandler
import org.commcare.core.network.AuthInfo

class PersonalIdAuthService(
    private val activity: Activity,
    private val authInfo: AuthInfo,
    private val callback: OtpVerificationCallback,
) : OtpAuthService {
    override fun requestOtp(phoneNumber: String) {
        object : PersonalIdApiHandler<PersonalIdSessionData?>() {
            override fun onSuccess(sessionData: PersonalIdSessionData?) {
                callback.onCodeSent(null)
            }

            override fun onFailure(
                failureCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                callback.onPersonalIdApiFailure(failureCode, t)
            }
        }.sendPhoneOtp(activity, authInfo)
    }

    override fun verifyOtp(code: String) {
        // no verification step just call submit directly
        submitOtp(code)
    }

    override fun submitOtp(code: String) {
        object : PersonalIdApiHandler<PersonalIdSessionData?>() {
            override fun onSuccess(sessionData: PersonalIdSessionData?) {
                callback.onSuccess()
            }

            override fun onFailure(
                failureCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?,
            ) {
                callback.onPersonalIdApiFailure(failureCode, t)
            }
        }.validatePhoneOtp(activity, code, authInfo)
    }
}
