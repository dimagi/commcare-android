package org.commcare.utils

import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.commcare.util.LogTypes
import org.javarosa.core.services.Logger

/**
 * Normalizes Firebase {@link OtpErrorType}, {@link PersonalIdOrConnectApiErrorCodes},
 * and the OtpManager SMS-method string into the stable analytics strings emitted with
 * the {@code otp_requested} event.
 */
object OtpAnalyticsMapper {
    /**
     * Maps the OtpManager SMS method string ([OtpManager.SMS_METHOD_FIREBASE] /
     * [OtpManager.SMS_METHOD_PERSONAL_ID]) to the analytics method constant.
     * Returns [AnalyticsParamValue.OTP_METHOD_FIREBASE] when the input is null
     * (Firebase is the default flow), and `"UNKNOWN-<input>"` for any other value
     * so unexpected SMS methods stay visible in BI.
     */
    @JvmStatic
    fun methodFromSmsMethod(smsMethod: String?): String? =
        when {
            smsMethod == null -> AnalyticsParamValue.OTP_METHOD_FIREBASE
            smsMethod.equals(OtpManager.SMS_METHOD_PERSONAL_ID, ignoreCase = true) ->
                AnalyticsParamValue.OTP_METHOD_PERSONAL_ID
            smsMethod.equals(OtpManager.SMS_METHOD_FIREBASE, ignoreCase = true) ->
                AnalyticsParamValue.OTP_METHOD_FIREBASE
            else -> "UNKNOWN-$smsMethod"
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

    /** Which OTP call is currently in flight, used to attribute the next callback. */
    enum class OtpOp {
        REQUEST,
        VERIFY,
    }

    @JvmStatic
    fun getEventType(currentOtpOp: OtpOp?): String? =
        when {
            OtpOp.REQUEST == currentOtpOp -> AnalyticsParamValue.OTP_EVENT_TYPE_REQUEST

            OtpOp.VERIFY == currentOtpOp -> AnalyticsParamValue.OTP_EVENT_TYPE_VERIFY

            else -> {
                Logger.log(
                    LogTypes.TYPE_ERROR_DESIGN,
                    "reportOtpAnalytics called with null OtpOp",
                )
                null
            }
        }
}
