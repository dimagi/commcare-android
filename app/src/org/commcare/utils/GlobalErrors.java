package org.commcare.utils;

import org.commcare.dalvik.R;

public enum GlobalErrors {
    PERSONALID_GENERIC_ERROR(R.string.personalid_generic_error),
    PERSONALID_LOST_CONFIGURATION_ERROR(R.string.personalid_token_request_rejected),
    PERSONALID_DB_STARTUP_ERROR(R.string.personalid_generic_error),
    PERSONALID_DB_UPGRADE_ERROR(R.string.personalid_generic_error),
    PERSONALID_LOGIN_FROM_DIFFERENT_DEVICE_ERROR(R.string.personalid_login_from_different_device);

    final int messageId;

    GlobalErrors(int messageId) {
        this.messageId = messageId;
    }

    public int getMessageId() {
        return messageId;
    }
}
