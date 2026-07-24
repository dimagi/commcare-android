package org.commcare.connect

import android.content.Context
import org.commcare.android.database.connect.models.ConnectReleaseToggleRecord
import org.commcare.android.database.connect.models.PersonalIdSessionData
import org.commcare.connect.database.ConnectAppDatabaseUtil

object ReleaseToggleHelper {
    private const val EMAIL_OTP_VERIFICATION_SLUG = "email_otp_verification"

    fun isToggleActive(
        sessionData: PersonalIdSessionData?,
        slug: String,
    ): Boolean = evaluate(sessionData?.featureReleaseToggles, slug)

    fun isToggleActive(
        context: Context,
        slug: String,
    ): Boolean = evaluate(ConnectAppDatabaseUtil.getReleaseToggles(context), slug)

    private fun evaluate(
        toggles: List<ConnectReleaseToggleRecord>?,
        slug: String,
    ): Boolean = toggles?.firstOrNull { it.slug == slug }?.active == true

    fun isEmailOtpVerificationActive(sessionData: PersonalIdSessionData?): Boolean =
        isToggleActive(sessionData, EMAIL_OTP_VERIFICATION_SLUG)

    fun isEmailOtpVerificationActive(context: Context): Boolean = isToggleActive(context, EMAIL_OTP_VERIFICATION_SLUG)
}
