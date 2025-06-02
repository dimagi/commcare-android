package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.android.database.global.models.GlobalErrorRecord;
import org.commcare.connect.PersonalIdManager;
import org.commcare.google.services.analytics.AnalyticsParamValue;
import org.commcare.models.database.SqlStorage;

import java.util.Vector;

public class GlobalErrorUtil {
    public static void addError(GlobalErrorRecord error) {
        CommCareApplication.instance().getGlobalStorage(GlobalErrorRecord.class).write(error);
    }

    public static String handleGlobalErrors() {
        StringBuilder sb = new StringBuilder();
        boolean deleteConnectStorage = false;
        SqlStorage<GlobalErrorRecord> storage = CommCareApplication.instance()
                .getGlobalStorage(GlobalErrorRecord.class);
        Vector<GlobalErrorRecord> errors = storage.getRecordsForValues(new String[]{}, new String[]{});

        if(!errors.isEmpty()) {
            for (GlobalErrorRecord error : errors) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }

                GlobalErrors ge = GlobalErrors.values()[error.getErrorCode()];

                sb.append(CommCareApplication.instance().getString(ge.getMessageId()));

                deleteConnectStorage |= ge == GlobalErrors.PERSONALID_GENERIC_ERROR
                        || ge == GlobalErrors.PERSONALID_LOST_CONFIGURATION_ERROR;
            }

            if(deleteConnectStorage) {
                PersonalIdManager.getInstance().forgetUser(AnalyticsParamValue.CCC_DB_ERROR);
            }

            //Clear the errors once retrieved
            storage.removeAll();
        }

        return sb.toString();
    }
}
