package org.commcare.utils;

import org.commcare.dalvik.R;

public enum GlobalErrors {
        PERSONALID_GENERIC_ERROR(
                        R.string.personalid_generic_error_title,
                        R.string.personalid_generic_error),
        PERSONALID_LOST_CONFIGURATION_ERROR(
                        R.string.personalid_generic_error_title,
                        R.string.personalid_token_request_rejected),
        PERSONALID_DB_STARTUP_ERROR(
                        R.string.personalid_generic_error_title,
                        R.string.personalid_generic_error),
        PERSONALID_DB_UPGRADE_ERROR(
                        R.string.personalid_generic_error_title,
                        R.string.personalid_generic_error),
        PERSONALID_LOGIN_FROM_DIFFERENT_DEVICE_ERROR(
                        R.string.personalid_error_title_login_different_device,
                        R.string.personalid_error_message_login_different_device);

        final int titleId;
        final int messageId;

        GlobalErrors(int titleId, int messageId) {
                this.titleId = titleId;
                this.messageId = messageId;
        }

        public int getMessageId() {
                return messageId;
        }

        public int getTitleId() {
                return titleId;
        }
}
