package org.commcare.connect.database;

import android.content.Context;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectReleaseToggleRecord;
import org.commcare.android.database.connect.models.PersonalIdWorkHistory;
import org.commcare.connect.PersonalIdManager;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.models.database.SqlStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class ConnectAppDatabaseUtil {
    public static ConnectLinkedAppRecord getConnectLinkedAppRecord(Context context, String appId, String username) {
        if (PersonalIdManager.getInstance().isloggedIn()) {
            Vector<ConnectLinkedAppRecord> records = ConnectDatabaseHelper.getConnectStorage(context, ConnectLinkedAppRecord.class)
                    .getRecordsForValues(
                            new String[]{ConnectLinkedAppRecord.META_APP_ID, ConnectLinkedAppRecord.META_USER_ID},
                            new Object[]{appId, username});
            return records.isEmpty() ? null : records.firstElement();
        }
        return null;
    }

    public static void deleteAppData(Context context, ConnectLinkedAppRecord record) {
        SqlStorage<ConnectLinkedAppRecord> storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectLinkedAppRecord.class);
        storage.remove(record);
    }

    /**
     * Stores or updates a ConnectLinkedAppRecord in the database.
     *
     * @param context         The Android context
     * @param appId           Application identifier
     * @param userId          User identifier
     * @param connectIdLinked Whether the app is linked to ConnectID
     * @param passwordOrPin   User's password or PIN
     * @param workerLinked    Whether the app is linked to a worker
     * @param localPassphrase Whether using local passphrase
     * @return The stored record
     * throw error if storage operations fail
     */
    public static ConnectLinkedAppRecord storeApp(Context context, String appId, String userId, boolean connectIdLinked, String passwordOrPin, boolean workerLinked, boolean localPassphrase) {

        ConnectLinkedAppRecord record = getConnectLinkedAppRecord(context, appId, userId);
        if (record == null) {
            record = new ConnectLinkedAppRecord(appId, userId, connectIdLinked, passwordOrPin);
        } else if (!record.getPassword().equals(passwordOrPin)) {
            record.setPassword(passwordOrPin);
        }

        record.setPersonalIdLinked(connectIdLinked);
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

    public static void storeCredentialDataInTable(Context context, List<PersonalIdWorkHistory> validCredentials) {
        SqlStorage<PersonalIdWorkHistory> storage =
                ConnectDatabaseHelper.getConnectStorage(context, PersonalIdWorkHistory.class);

        storage.removeAll();

        for (PersonalIdWorkHistory credential : validCredentials) {
            storage.write(credential);
        }
    }

    public static void storeReleaseToggles(
            Context context,
            List<ConnectReleaseToggleRecord> incomingToggles
    ) {
        List<ConnectReleaseToggleRecord> changedToggles = new ArrayList<>();

        try {
            SqlStorage<ConnectReleaseToggleRecord> toggleStorage =
                    ConnectDatabaseHelper.getConnectStorage(context, ConnectReleaseToggleRecord.class);
            ConnectDatabaseHelper.connectDatabase.beginTransaction();

            List<ConnectReleaseToggleRecord> existingToggles = getReleaseToggles(context);

            toggleStorage.removeAll();

            for (ConnectReleaseToggleRecord incomingToggle : incomingToggles) {
                toggleStorage.write(incomingToggle);

                boolean toggleIsNewOrModified = true;
                for (ConnectReleaseToggleRecord existingToggle : existingToggles) {
                    boolean slugsMatch = existingToggle.getSlug().equals(incomingToggle.getSlug());
                    boolean activeStatusesMatch = existingToggle.getActive() == incomingToggle.getActive();
                    if (slugsMatch && activeStatusesMatch) {
                        toggleIsNewOrModified = false;
                        break;
                    }
                }

                if (toggleIsNewOrModified) {
                    changedToggles.add(incomingToggle);
                }
            }

            ConnectDatabaseHelper.connectDatabase.setTransactionSuccessful();
        } finally {
            ConnectDatabaseHelper.connectDatabase.endTransaction();
        }

        if (!changedToggles.isEmpty()) {
            FirebaseAnalyticsUtil.reportPersonalIdReleaseTogglesChanged(changedToggles);
        }
    }

    public static List<ConnectReleaseToggleRecord> getReleaseToggles(Context context) {
        SqlStorage<ConnectReleaseToggleRecord> toggleStorage =
                ConnectDatabaseHelper.getConnectStorage(context, ConnectReleaseToggleRecord.class);

        return toggleStorage.getRecordsForValues(new String[]{}, new Object[]{});
    }
}
