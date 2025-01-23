package org.commcare.connect.database;

import android.content.Context;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.models.database.SqlStorage;

import java.util.Vector;

public class ConnectAppDatabaseUtil {
    public static ConnectLinkedAppRecord getAppData(Context context, String appId, String username) {
        Vector<ConnectLinkedAppRecord> records = ConnectDatabaseHelper.getConnectStorage(context, ConnectLinkedAppRecord.class)
                .getRecordsForValues(
                        new String[]{ConnectLinkedAppRecord.META_APP_ID, ConnectLinkedAppRecord.META_USER_ID},
                        new Object[]{appId, username});
        return records.isEmpty() ? null : records.firstElement();
    }

    public static void deleteAppData(Context context, ConnectLinkedAppRecord record) {
        SqlStorage<ConnectLinkedAppRecord> storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectLinkedAppRecord.class);
        storage.remove(record);
    }

    public static ConnectLinkedAppRecord storeApp(Context context, String appId, String userId, boolean connectIdLinked, String passwordOrPin, boolean workerLinked, boolean localPassphrase) {
        ConnectLinkedAppRecord record = getAppData(context, appId, userId);
        if (record == null) {
            record = new ConnectLinkedAppRecord(appId, userId, connectIdLinked, passwordOrPin);
        } else if (!record.getPassword().equals(passwordOrPin)) {
            record.setPassword(passwordOrPin);
        }

        record.setConnectIdLinked(connectIdLinked);
        record.setIsUsingLocalPassphrase(localPassphrase);

        if (workerLinked) {
            //If passed in false, we'll leave the setting unchanged
            record.setWorkerLinked(true);
        }

        storeApp(context, record);

        return record;
    }

    public static void storeApp(Context context, ConnectLinkedAppRecord record) {
        ConnectDatabaseHelper.getConnectStorage(context, ConnectLinkedAppRecord.class).write(record);
    }
}
