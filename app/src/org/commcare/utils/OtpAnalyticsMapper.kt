package org.commcare.utils

import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.google.services.analytics.AnalyticsParamValue

/**
 * Normalizes Firebase {@link OtpErrorType}, {@link PersonalIdOrConnectApiErrorCodes},
 * and the OtpManager SMS-method string into the stable analytics strings emitted with
 * the {@code otp_requested} event (CCCT-2052).
 */
object OtpAnalyticsMapper {
    /**
     * Maps the OtpManager SMS method string ({@link OtpManager#SMS_METHOD_FIREBASE} /
     * {@link OtpManager#SMS_METHOD_PERSONAL_ID}) to the analytics method constant.
     * Falls back to {@link AnalyticsParamValue#OTP_METHOD_FIREBASE} when the input is
     * null or unrecognized so events always carry a method value.
     */
    @JvmStatic
    fun methodFromSmsMethod(smsMethod: String?): String =
        when {
            smsMethod == null -> AnalyticsParamValue.OTP_METHOD_FIREBASE
            smsMethod.equals(OtpManager.SMS_METHOD_PERSONAL_ID, ignoreCase = true) ->
                AnalyticsParamValue.OTP_METHOD_PERSONAL_ID
            smsMethod.equals(OtpManager.SMS_METHOD_FIREBASE, ignoreCase = true) ->
                AnalyticsParamValue.OTP_METHOD_FIREBASE
            else -> AnalyticsParamValue.OTP_METHOD_FIREBASE
        }

    /**
     * Maps a Firebase {@link OtpErrorType} to a stable lowercase reason string suitable
     * for analytics. Returns null when the input is null.
     */
    @JvmStatic
    fun reasonFrom(errorType: OtpErrorType?): String? = errorType?.name?.lowercase()

    /**
     * Maps a PersonalID/Connect API error code to a stable lowercase reason string
     * suitable for analytics. Returns null when the input is null.
     */
    @JvmStatic
    fun reasonFrom(errorCode: PersonalIdOrConnectApiErrorCodes?): String? = errorCode?.name?.lowercase()
}
