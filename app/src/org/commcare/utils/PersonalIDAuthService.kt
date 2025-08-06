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
                callback.onPersonalIdApiFailure(failureCode, t)
            }
        }.sendOtp(activity, personalIdSessionData)
    }

    override fun verifyOtp(code: String) {
        // no verification step just call submit directly
        submitOtp(code);
    }

    override fun submitOtp(code: String) {
        object : PersonalIdApiHandler<PersonalIdSessionData>() {
            override fun onSuccess(sessionData: PersonalIdSessionData) {
                callback.onSuccess()
            }

            override fun onFailure(failureCode: PersonalIdOrConnectApiErrorCodes, t: Throwable?) {
                callback.onPersonalIdApiFailure(failureCode, t)
            }
        }.validateOtp(activity, code, personalIdSessionData)
    }
}
