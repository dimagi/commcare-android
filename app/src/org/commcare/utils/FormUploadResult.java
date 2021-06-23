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
     * Form processing resulted in introducing an invalid case relationship most probably resulting in cycles into case graph
     */
    INVALID_CASE_GRAPH(3),

    /**
     * HQ received the form submission but encountered an error while processing it, so the
     * form has not resulted in any changes to the HQ database
     */
    PROCESSING_FAILURE(4),


    /**
     * We attempted an authenticated request over http
     */
    AUTH_OVER_HTTP(5),

    /**
     * The server returned an authentication error
     */
    AUTH_FAILURE(6),

    /**
     * There was a problem with the transport layer during transit
     */
    TRANSPORT_FAILURE(7),

    /**
     * Server has some action directives for user to resolve this error
     */
    ACTIONABLE_FAILURE(8),

    /**
     * The user session ended while trying to upload a form
     */
    PROGRESS_LOGGED_OUT(9),

    PROGRESS_SDCARD_REMOVED(10),

    /**
     * The server can't couldn't handle the submission due to load, we
     * shouldn't keep retrying it
     */
    RATE_LIMITED(11),

    /**
     * User is behind a captive portal, no need to try re-submissions
     */
    CAPTIVE_PORTAL(12)
    ;

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
