package org.commcare.utils;

import org.commcare.views.notifications.MessageTag;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public enum FormUploadResult implements MessageTag {
    /**
     * Everything worked great!
     */
    FULL_SUCCESS(0, ""),

    /**
     * Non-specific error used as default assumption before attempting submission, and when
     * certain exceptions are encountered (IOException or TaskCancelledException)
     */
    FAILURE(1, "sync.fail.unknown"),

    /**
     * There is a problem with this record that prevented submission success for this record
     * specifically, but others will be allowed to continue trying
     */
    RECORD_FAILURE(2, "sync.fail.individual"),

    /**
     * Form processing resulted in introducing an invalid case relationship most probably resulting in cycles into case graph
     */
    INVALID_CASE_GRAPH(3, "sync.fail.invalid.case.graph"),

    /**
     * HQ received the form submission but encountered an error while processing it, so the
     * form has not resulted in any changes to the HQ database
     */
    PROCESSING_FAILURE(4, "sync.fail.server.error"),


    /**
     * We attempted an authenticated request over http
     */
    AUTH_OVER_HTTP(5, "sync.fail.unknown"),

    /**
     * The server returned an authentication error
     */
    AUTH_FAILURE(6, "sync.fail.auth.loggedin"),

    /**
     * There was a problem with the transport layer during transit
     */
    TRANSPORT_FAILURE(7, "sync.fail.bad.network"),

    /**
     * Server has some action directives for user to resolve this error
     */
    ACTIONABLE_FAILURE(8, ""),

    /**
     * The user session ended while trying to upload a form
     */
    PROGRESS_LOGGED_OUT(9, "sync.fail.unknown"),

    PROGRESS_SDCARD_REMOVED(10, "sync.fail.unknown"),

    /**
     * The server can't couldn't handle the submission due to load, we
     * shouldn't keep retrying it
     */
    RATE_LIMITED(11, "form.send.rate.limit.error.toast"),

    /**
     * User is behind a captive portal, no need to try re-submissions
     */
    CAPTIVE_PORTAL(12, "sync.fail.unknown"),

    /**
     * Error involving SSL certificate, no need to try re-submission
     * This is often related to local clock settings
     */
    BAD_CERTIFICATE(13, "sync.fail.badcert"),
    /**
     * Delivery error
     */
    UNSENT(14, "sync.fail.unsent"),
    /**
     * Upload cancelled
     */
    CANCELLED(15, "activity.task.cancelled.message"),
    ;

    private final int orderVal;
    private String errorMessage;
    private final String root;

    FormUploadResult(int orderVal, String root) {
        this.orderVal = orderVal;
        this.root = root;
    }

    @Override
    public String getLocaleKeyBase() {
        return root;
    }

    public String getCategory() {
        return "form_upload";
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
