package org.commcare.utils;

import org.commcare.CommCareApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.database.global.models.GlobalErrorRecord;
import org.commcare.connect.network.LoginInvalidatedException;
import org.commcare.models.database.SqlStorage;

import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class GlobalErrorUtil {
    private static final int ERROR_EXPIRATION_HOURS = 24;

    public static boolean triggerGlobalError(GlobalErrors error) {
        if (FormEntryActivity.mFormController != null) {
            return false;
        }

        throw new LoginInvalidatedException(error);
    }

    public static void addError(GlobalErrorRecord error) {
        CommCareApplication.instance().getGlobalStorage(GlobalErrorRecord.class).write(error);
    }

    public static String checkGlobalErrors() {
        SqlStorage<GlobalErrorRecord> storage = CommCareApplication.instance()
                .getGlobalStorage(GlobalErrorRecord.class);

        pruneOldErrors(storage);

        Vector<GlobalErrorRecord> errors = storage.getRecordsForValues(new String[]{}, new String[]{});
        if (errors.isEmpty()) {
            return null;
        }

        GlobalErrors ge = GlobalErrors.values()[errors.get(0).getErrorCode()];
        return CommCareApplication.instance().getString(ge.getMessageId());
    }

    private static void pruneOldErrors(SqlStorage<GlobalErrorRecord> storage) {
        long currentTime = System.currentTimeMillis();
        long expirationWindow = TimeUnit.HOURS.toMillis(ERROR_EXPIRATION_HOURS);

        Vector<GlobalErrorRecord> errors = storage.getRecordsForValues(new String[]{}, new String[]{});
        for (GlobalErrorRecord error : errors) {
            if (currentTime - error.getCreatedDate().getTime() > expirationWindow) {
                storage.remove(error.getID());
            }
        }
    }

    public static void dismissGlobalErrors() {
        SqlStorage<GlobalErrorRecord> storage = CommCareApplication.instance()
                .getGlobalStorage(GlobalErrorRecord.class);
        storage.removeAll();
    }
}
