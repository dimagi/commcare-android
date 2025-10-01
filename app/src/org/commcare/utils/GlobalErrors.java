package org.commcare.utils;

import org.commcare.dalvik.R;

public enum GlobalErrors {
    PERSONALID_GENERIC_ERROR(R.string.personalid_generic_error),
    PERSONALID_LOST_CONFIGURATION_ERROR(R.string.recovery_network_token_request_rejected),
    PERSONALID_DB_STARTUP_ERROR(R.string.personalid_generic_error),
    PERSONALID_DB_UPGRADE_ERROR(R.string.personalid_generic_error);

    final int messageId;

    GlobalErrors(int messageId) {
        this.messageId = messageId;
    }

    public int getMessageId() {
        return messageId;
    }
}
