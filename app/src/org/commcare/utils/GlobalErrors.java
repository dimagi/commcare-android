package org.commcare.utils;

import org.commcare.dalvik.R;

public enum GlobalErrors {
    PERSONALID_GENERIC_ERROR(R.string.personalid_generic_error),
    PERSONALID_LOST_CONFIGURATION_ERROR(R.string.recovery_network_token_request_rejected);

    final int messageId;

    GlobalErrors(int messageId) {
        this.messageId = messageId;
    }

    public int getMessageId() {
        return messageId;
    }
}
