package org.commcare.utils;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public enum FormUploadResult {
    FULL_SUCCESS(0),
    FAILURE(1),
    AUTH_FAILURE(2),
    TRANSPORT_FAILURE(3),
    RECORD_FAILURE(4),
    PROGRESS_LOGGED_OUT(5),
    PROGRESS_SDCARD_REMOVED(6);

    private final int orderVal;

    FormUploadResult(int orderVal) {
        this.orderVal = orderVal;
    }

    public static FormUploadResult getWorstResult(FormUploadResult[] results) {
        FormUploadResult worstResult = FULL_SUCCESS;
        for (FormUploadResult result : results) {
            if (result.orderVal > worstResult.orderVal) {
                worstResult = result;
            }
        }
        return worstResult;
    }
}
