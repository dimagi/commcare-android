package org.commcare.utils;

import org.commcare.dalvik.R;

public enum GlobalErrors {
    PERSONALID_GENERIC_ERROR(R.string.personalid_generic_error),
    PERSONALID_LOST_CONFIGURATION_ERROR(R.string.recovery_network_token_request_rejected),
    PERSONALID_BIOMETRIC_INVALIDATED_ERROR(R.string.personalid_biometric_invalidated_error);

    final int messageId;

    GlobalErrors(int messageId) {
        this.messageId = messageId;
    }

    public int getMessageId() {
        return messageId;
    }
}
