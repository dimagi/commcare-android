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
     * Non-specific error used as default assumption before attempting submission, and when
     * certain exceptions are encountered (IOException or TaskCancelledException)
     */
    FAILURE(1),

    /**
     * There is a problem with this record that prevented submission success for this record
     * specifically, but others will be allowed to continue trying
     */
    RECORD_FAILURE(2),

    /**
     * HQ received the form submission but encountered an error while processing it, so the
     * form has not resulted in any changes to the HQ database
     */
    PROCESSING_FAILURE(3),

    /**
     * We attempted an authenticated request over http
     */
    AUTH_OVER_HTTP(4),

    /**
     * The server returned an authentication error
     */
    AUTH_FAILURE(5),

    /**
     * There was a problem with the transport layer during transit
     */
    TRANSPORT_FAILURE(6),

    /**
     * Server has some action directives for user to resolve this error
     */
    ACTIONABLE_FAILURE(7),

    /**
     * The user session ended while trying to upload a form
     */
    PROGRESS_LOGGED_OUT(8),

    PROGRESS_SDCARD_REMOVED(9);

    private final int orderVal;
    private String errorMessage;

    FormUploadResult(int orderVal) {
        this.orderVal = orderVal;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
