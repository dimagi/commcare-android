package org.commcare.utils

import org.commcare.connect.network.base.BaseApiHandler.PersonalIdOrConnectApiErrorCodes
import org.commcare.fragments.personalId.EmailWorkFlow
import org.commcare.google.services.analytics.AnalyticsParamValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpAnalyticsMapperTest {
    // --- methodFromSmsMethod ---

    @Test
    fun `methodFromSmsMethod returns firebase for SMS_METHOD_FIREBASE`() {
        assertEquals(
            AnalyticsParamValue.OTP_METHOD_FIREBASE,
            OtpAnalyticsMapper.methodFromSmsMethod(OtpManager.SMS_METHOD_FIREBASE),
        )
    }

    @Test
    fun `methodFromSmsMethod returns personal_id for SMS_METHOD_PERSONAL_ID`() {
        assertEquals(
            AnalyticsParamValue.OTP_METHOD_PERSONAL_ID,
            OtpAnalyticsMapper.methodFromSmsMethod(OtpManager.SMS_METHOD_PERSONAL_ID),
        )
    }

    @Test
    fun `methodFromSmsMethod ignores case`() {
        assertEquals(
            AnalyticsParamValue.OTP_METHOD_PERSONAL_ID,
            OtpAnalyticsMapper.methodFromSmsMethod("Personal_ID"),
        )
        assertEquals(
            AnalyticsParamValue.OTP_METHOD_FIREBASE,
            OtpAnalyticsMapper.methodFromSmsMethod("FIREBASE"),
        )
    }

    @Test
    fun `methodFromSmsMethod defaults to firebase when null`() {
        assertEquals(
            AnalyticsParamValue.OTP_METHOD_FIREBASE,
            OtpAnalyticsMapper.methodFromSmsMethod(null),
        )
    }

    @Test
    fun `methodFromSmsMethod prefixes UNKNOWN for unrecognized values`() {
        assertEquals(
            "UNKNOWN-twilio",
            OtpAnalyticsMapper.methodFromSmsMethod("twilio"),
        )
    }

    // --- reasonFrom(OtpErrorType) ---

    @Test
    fun `reasonFrom OtpErrorType maps INVALID_CREDENTIAL`() {
        assertEquals(
            "invalid_credential",
            OtpAnalyticsMapper.reasonFrom(OtpErrorType.INVALID_CREDENTIAL),
        )
    }

    @Test
    fun `reasonFrom OtpErrorType maps TOO_MANY_REQUESTS`() {
        assertEquals(
            "too_many_requests",
            OtpAnalyticsMapper.reasonFrom(OtpErrorType.TOO_MANY_REQUESTS),
        )
    }

    @Test
    fun `reasonFrom OtpErrorType maps MISSING_ACTIVITY`() {
        assertEquals(
            "missing_activity",
            OtpAnalyticsMapper.reasonFrom(OtpErrorType.MISSING_ACTIVITY),
        )
    }

    @Test
    fun `reasonFrom OtpErrorType maps GENERIC_ERROR`() {
        assertEquals(
            "generic_error",
            OtpAnalyticsMapper.reasonFrom(OtpErrorType.GENERIC_ERROR),
        )
    }

    @Test
    fun `reasonFrom OtpErrorType maps VERIFICATION_FAILED`() {
        assertEquals(
            "verification_failed",
            OtpAnalyticsMapper.reasonFrom(OtpErrorType.VERIFICATION_FAILED),
        )
    }

    @Test
    fun `reasonFrom OtpErrorType returns null for null input`() {
        assertNull(OtpAnalyticsMapper.reasonFrom(null as OtpErrorType?))
    }

    // --- reasonFrom(PersonalIdOrConnectApiErrorCodes) ---

    @Test
    fun `reasonFrom api code maps INCORRECT_OTP_ERROR`() {
        assertEquals(
            "incorrect_otp_error",
            OtpAnalyticsMapper.reasonFrom(PersonalIdOrConnectApiErrorCodes.INCORRECT_OTP_ERROR),
        )
    }

    @Test
    fun `reasonFrom api code maps NETWORK_ERROR`() {
        assertEquals(
            "network_error",
            OtpAnalyticsMapper.reasonFrom(PersonalIdOrConnectApiErrorCodes.NETWORK_ERROR),
        )
    }

    @Test
    fun `reasonFrom api code maps RATE_LIMIT_EXCEEDED_ERROR`() {
        assertEquals(
            "rate_limit_exceeded_error",
            OtpAnalyticsMapper.reasonFrom(PersonalIdOrConnectApiErrorCodes.RATE_LIMIT_EXCEEDED_ERROR),
        )
    }

    @Test
    fun `reasonFrom api code maps FORBIDDEN_ERROR`() {
        assertEquals(
            "forbidden_error",
            OtpAnalyticsMapper.reasonFrom(PersonalIdOrConnectApiErrorCodes.FORBIDDEN_ERROR),
        )
    }

    @Test
    fun `reasonFrom api code maps UNKNOWN_ERROR`() {
        assertEquals(
            "unknown_error",
            OtpAnalyticsMapper.reasonFrom(PersonalIdOrConnectApiErrorCodes.UNKNOWN_ERROR),
        )
    }

    @Test
    fun `reasonFrom api code maps FAILED_AUTH_ERROR`() {
        assertEquals(
            "failed_auth_error",
            OtpAnalyticsMapper.reasonFrom(PersonalIdOrConnectApiErrorCodes.FAILED_AUTH_ERROR),
        )
    }

    @Test
    fun `reasonFrom api code returns null for null input`() {
        assertNull(OtpAnalyticsMapper.reasonFrom(null as PersonalIdOrConnectApiErrorCodes?))
    }

    // --- getEventType ---

    @Test
    fun `getEventType returns request for REQUEST_PHONE`() {
        assertEquals(
            AnalyticsParamValue.OTP_EVENT_TYPE_REQUEST,
            OtpAnalyticsMapper.getEventType(OtpAnalyticsMapper.OtpOp.REQUEST_PHONE),
        )
    }

    @Test
    fun `getEventType returns verify for VERIFY_PHONE`() {
        assertEquals(
            AnalyticsParamValue.OTP_EVENT_TYPE_VERIFY,
            OtpAnalyticsMapper.getEventType(OtpAnalyticsMapper.OtpOp.VERIFY_PHONE),
        )
    }

    @Test
    fun `getEventType returns request_email for REQUEST_EMAIL`() {
        assertEquals(
            AnalyticsParamValue.OTP_EVENT_TYPE_REQUEST_EMAIL,
            OtpAnalyticsMapper.getEventType(OtpAnalyticsMapper.OtpOp.REQUEST_EMAIL),
        )
    }

    @Test
    fun `getEventType returns verify_email for VERIFY_EMAIL`() {
        assertEquals(
            AnalyticsParamValue.OTP_EVENT_TYPE_VERIFY_EMAIL,
            OtpAnalyticsMapper.getEventType(OtpAnalyticsMapper.OtpOp.VERIFY_EMAIL),
        )
    }

    @Test
    fun `getEventType returns null for null input`() {
        assertNull(OtpAnalyticsMapper.getEventType(null))
    }

    // --- workflowParam ---

    @Test
    fun `workflowParam lowercases REGISTRATION`() {
        assertEquals("registration", OtpAnalyticsMapper.workflowParam(EmailWorkFlow.REGISTRATION))
    }

    @Test
    fun `workflowParam lowercases RECOVERY`() {
        assertEquals("recovery", OtpAnalyticsMapper.workflowParam(EmailWorkFlow.RECOVERY))
    }

    @Test
    fun `workflowParam lowercases EXISTING_USER`() {
        assertEquals("existing_user", OtpAnalyticsMapper.workflowParam(EmailWorkFlow.EXISTING_USER))
    }
}
