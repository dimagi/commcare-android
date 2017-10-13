package org.commcare.utils;

import org.commcare.android.database.user.models.FormRecord;
import org.commcare.views.notifications.MessageTag;
import org.commcare.views.notifications.NotificationMessage;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.ProcessIssues;
import org.javarosa.core.services.locale.Localization;

/**
 * Created by amstone326 on 9/25/17.
 */

public class QuarantineUtil {

    public static String getQuarantineReasonDisplayString(FormRecord r, boolean includeDetail) {
        StringBuilder builder = new StringBuilder();
        switch (r.getQuarantineReasonType()) {
            case FormRecord.QuarantineReason_LOCAL_PROCESSING_ERROR:
                builder.append(Localization.get("quarantine.reason.local.processing"));
                break;
            case FormRecord.QuarantineReason_SERVER_PROCESSING_ERROR:
                builder.append(Localization.get("quarantine.reason.server.processing"));
                break;
            case FormRecord.QuarantineReason_RECORD_ERROR:
                builder.append(Localization.get("quarantine.reason.record.error"));
                break;
            case FormRecord.QuarantineReason_MANUAL:
                builder.append(Localization.get("quarantine.reason.manual"));
                break;
            case FormRecord.QuarantineReason_FILE_NOT_FOUND:
                builder.append(Localization.get("quarantine.reason.fnf"));
                break;
            default:
                builder.append(Localization.get("quarantine.reason.unknown"));
        }

        if (includeDetail) {
            String detail = r.getQuarantineReasonDetail();
            if (detail != null) {
                builder.append(": " + detail);
            }
        }

        return builder.toString();
    }

    public static NotificationMessage getQuarantineNotificationMessage(FormRecord r) {
        MessageTag tag;
        switch (r.getQuarantineReasonType()) {
            case FormRecord.QuarantineReason_LOCAL_PROCESSING_ERROR:
                tag = ProcessIssues.RecordQuarantinedLocalProcessingIssue;
                break;
            case FormRecord.QuarantineReason_SERVER_PROCESSING_ERROR:
                tag = ProcessIssues.RecordQuarantinedServerIssue;
                break;
            case FormRecord.QuarantineReason_RECORD_ERROR:
                tag = ProcessIssues.RecordQuarantinedRecordIssue;
                break;
            case FormRecord.QuarantineReason_FILE_NOT_FOUND:
                tag = ProcessIssues.RecordFilesMissing;
                break;
            case FormRecord.QuarantineReason_MANUAL:
            default:
                return null;
        }
        return NotificationMessageFactory.message(tag);
    }
}
