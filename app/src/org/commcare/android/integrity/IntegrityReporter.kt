package org.commcare.android.integrity

import android.app.Activity
import androidx.preference.PreferenceManager
import org.commcare.CommCareApplication
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.android.integrity.IntegrityTokenViewModel.IntegrityTokenCallback
import org.commcare.android.logging.ReportingUtils
import org.commcare.connect.network.connectId.PersonalIdApiHandler
import org.commcare.preferences.HiddenPreferences

class IntegrityReporter {
    fun handleReceivedIntegrityRequest(activity: Activity, requestId: String) {
        val preferences =
            PreferenceManager.getDefaultSharedPreferences(CommCareApplication.instance())
        val storedId = preferences.getString(HiddenPreferences.INTEGRITY_REQUEST_ID, null)
        if (storedId == null || storedId != requestId) {
            val integrityTokenApiRequestHelper =
                IntegrityTokenApiRequestHelper(activity)
            val body = HashMap<String, String>()
            body["request_id"] = requestId
            body["cc_device_id"] = ReportingUtils.getDeviceId()

            integrityTokenApiRequestHelper.withIntegrityToken(
                body,
                object : IntegrityTokenCallback {
                    override fun onTokenReceived(token: String, requestHash: String) {
                        makeReportIntegrityCall(activity, token, requestHash, body)
                    }

                    override fun onTokenFailure(exception: Exception) {
                        makeReportIntegrityCall(activity, exception.message, "ERROR", body)
                    }
                })
        }
        if (preferences.getBoolean(HiddenPreferences.INTEGRITY_REQUEST_ID, true)) {
            val editor = preferences.edit()
            editor.putBoolean(HiddenPreferences.INTEGRITY_REQUEST_ID, false)
            editor.apply()
        }
    }

    private fun makeReportIntegrityCall(
        activity: Activity,
        integrityToken: String?,
        requestHash: String,
        body: Map<String, String>
    ) {
        object : PersonalIdApiHandler<PersonalIdSessionData?>() {
            override fun onSuccess(data: PersonalIdSessionData?) {
            }

            override fun onFailure(
                errorCode: PersonalIdOrConnectApiErrorCodes,
                t: Throwable?
            ) {
            }
        }.makeIntegrityReportCall(activity, body, integrityToken, requestHash)
    }
}