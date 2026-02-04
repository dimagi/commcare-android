package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.database.global.models.GlobalErrorRecord;
import org.commcare.connect.network.LoginInvalidatedException;
import org.commcare.models.database.SqlStorage;

import java.util.Vector;

public class GlobalErrorUtil {
    public static boolean triggerGlobalError(GlobalErrors error) {
        if(FormEntryActivity.mFormController != null) {
            return false;
        }

        throw new LoginInvalidatedException(error);
    }

    public static void addError(GlobalErrorRecord error) {
        CommCareApplication.instance().getGlobalStorage(GlobalErrorRecord.class).write(error);
    }

    public static String getGlobalErrors() {
        StringBuilder sb = new StringBuilder();
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
            }

            //Clear the errors once retrieved
            storage.removeAll();
        }

        return sb.toString();
    }
}
