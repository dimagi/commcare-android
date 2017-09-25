package org.commcare.utils;

import org.commcare.android.database.user.models.FormRecord;
import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.ProcessIssues;

/**
 * Created by amstone326 on 9/25/17.
 */

public class QuarantineUtil {

    public static String getQuarantineReasonDisplayString(FormRecord r) {
        switch (r.getReasonForQuarantine()) {
            case FormRecord.QuarantineReason_LOCAL_PROCESSING_ERROR:
                return "Local Processing Error";
            case FormRecord.QuarantineReason_SERVER_PROCESSING_ERROR:
                return "Server Processing Error";
            case FormRecord.QuarantineReason_RECORD_ERROR:
                return "Local Record Issue";
            case FormRecord.QuarantineReason_MANUAL:
                return "Manual Quarantine by User";
            default:
                return "";
        }
    }

    public static NotificationMessage getQuarantineNotificationMessage(FormRecord r) {
        MessageTag tag;
        switch (r.getReasonForQuarantine()) {
            case FormRecord.QuarantineReason_LOCAL_PROCESSING_ERROR:
                tag = ProcessIssues.RecordQuarantinedLocalProcessingIssue;
                break;
            case FormRecord.QuarantineReason_SERVER_PROCESSING_ERROR:
                tag = ProcessIssues.RecordQuarantinedServerIssue;
                break;
            case FormRecord.QuarantineReason_RECORD_ERROR:
                tag = ProcessIssues.RecordQuarantinedRecordIssue;
                break;
            default:
                return null;
        }
        return NotificationMessageFactory.message(tag);
    }
}
