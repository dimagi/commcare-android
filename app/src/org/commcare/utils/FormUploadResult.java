package org.commcare.utils;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public enum FormUploadResult {
    /**
     * Everything worked great!
     */
    FULL_SUCCESS(0),

    /**
     * There was a problem with the server's response
     */
    FAILURE(1),

    /**
     * The server returned an authentication error
     */
    AUTH_FAILURE(2),

    /**
     * There was a problem with the transport layer during transit
     */
    TRANSPORT_FAILURE(3),

    /**
     * There is a problem with this record that prevented submission success
     */
    RECORD_FAILURE(4),

    /**
     * The user session ended while trying to upload a form
     */
    PROGRESS_LOGGED_OUT(5),

    PROGRESS_SDCARD_REMOVED(6),

    AUTH_OVER_HTTP(7);

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
