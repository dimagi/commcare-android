package org.commcare.connect.network.personalId

object PersonalIdApiEndpoints {
    const val TOKEN_URL = "/o/token/"
    const val HEARTBEAT_URL = "/users/heartbeat"
    const val VALIDATE_FIREBASE_ID_TOKEN = "/users/validate_firebase_id_token"
    const val CHECK_NAME = "/users/check_name"
    const val REPORT_INTEGRITY = "/users/report_integrity"
    const val START_CONFIGURATION = "/users/start_configuration"
    const val SEND_SESSION_OTP = "/users/send_session_otp"
    const val VALIDATE_SESSION_OTP = "/users/confirm_session_otp"
    const val SEND_EMAIL_OTP = "/users/send_email_otp"
    const val VERIFY_EMAIL_OTP = "/users/verify_email_otp"
    const val UPDATE_PROFILE = "/users/update_profile"
    const val COMPLETE_PROFILE = "/users/complete_profile"
    const val CONFIRM_BACKUP_CODE = "/users/recover/confirm_backup_code"
    const val SET_BACKUP_CODE = "/users/set_recovery_pin"
    const val CREDENTIALS = "/users/credentials"
    const val RETRIEVE_NOTIFICATIONS = "/messaging/retrieve_notifications/"
    const val UPDATE_NOTIFICATIONS = "/messaging/update_notification_received/"
    const val MESSAGE_CHANNEL_CONSENT_URL = "/messaging/update_consent/"
    const val MESSAGE_SEND_URL = "/messaging/send_message/"
    const val RELEASE_TOGGLES = "/toggles"
}
