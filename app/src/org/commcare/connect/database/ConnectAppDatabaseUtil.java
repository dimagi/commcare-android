package org.commcare.connect.database;

import org.commcare.android.database.connect.models.ConnectLinkedAppRecord;
import org.commcare.android.database.connect.models.ConnectReleaseToggleRecord;
import org.commcare.android.database.connect.models.PersonalIdWorkHistory;
import org.commcare.connect.PersonalIdManager;
import org.commcare.google.services.analytics.FirebaseAnalyticsUtil;
import org.commcare.models.database.SqlStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class ConnectAppDatabaseUtil {
    public static ConnectLinkedAppRecord getConnectLinkedAppRecord(String appId, String username) {
        if (PersonalIdManager.getInstance().isloggedIn()) {
            Vector<ConnectLinkedAppRecord> records = ConnectDatabaseHelper.getConnectStorage(
                            ConnectLinkedAppRecord.class)
                    .getRecordsForValues(
                            new String[]{ConnectLinkedAppRecord.META_APP_ID, ConnectLinkedAppRecord.META_USER_ID},
                            new Object[]{appId, username});
            return records.isEmpty() ? null : records.firstElement();
        }
        return null;
    }

    public static void deleteAppData(ConnectLinkedAppRecord record) {
        SqlStorage<ConnectLinkedAppRecord> storage = ConnectDatabaseHelper.getConnectStorage(
                ConnectLinkedAppRecord.class);
        storage.remove(record);
    }

    /**
     * Stores or updates a ConnectLinkedAppRecord in the database.
     *
     * @param appId           Application identifier
     * @param userId          User identifier
     * @param connectIdLinked Whether the app is linked to ConnectID
     * @param passwordOrPin   User's password or PIN
     * @param workerLinked    Whether the app is linked to a worker
     * @return The stored record
     * throw error if storage operations fail
     */
    public static ConnectLinkedAppRecord storeApp(String appId, String userId, boolean connectIdLinked, String passwordOrPin, boolean workerLinked) {

        ConnectLinkedAppRecord record = getConnectLinkedAppRecord(appId, userId);
        if (record == null) {
            record = new ConnectLinkedAppRecord(appId, userId, connectIdLinked, passwordOrPin);
        } else if (!record.getPassword().equals(passwordOrPin)) {
            record.setPassword(passwordOrPin);
        }

        record.setPersonalIdLinked(connectIdLinked);
        record.setIsUsingLocalPassphrase(false);

        if (workerLinked) {
            //If passed in false, we'll leave the setting unchanged
            record.setWorkerLinked(true);
        }

        storeApp(record);

        return record;
    }

    public static void storeApp(ConnectLinkedAppRecord record) {
        ConnectDatabaseHelper.getConnectStorage(ConnectLinkedAppRecord.class).write(record);
    }

    public static void storeCredentialDataInTable(List<PersonalIdWorkHistory> validCredentials) {
        SqlStorage<PersonalIdWorkHistory> storage =
                ConnectDatabaseHelper.getConnectStorage(PersonalIdWorkHistory.class);

        storage.removeAll();

        for (PersonalIdWorkHistory credential : validCredentials) {
            storage.write(credential);
        }
    }

    public static void storeReleaseToggles(
            List<ConnectReleaseToggleRecord> incomingToggles
    ) {
        boolean togglesChanged = false;

        SqlStorage<ConnectReleaseToggleRecord> toggleStorage =
                ConnectDatabaseHelper.getConnectStorage(ConnectReleaseToggleRecord.class);
        ConnectDatabaseHelper.connectDatabase.beginTransaction();
        try {

            // Create map of existing toggles for easy comparison to incoming toggles.
            List<ConnectReleaseToggleRecord> existingToggles = getReleaseToggles();
            Map<String, ConnectReleaseToggleRecord> existingTogglesMap = new HashMap<>();
            for (ConnectReleaseToggleRecord existingToggle : existingToggles) {
                existingTogglesMap.put(existingToggle.getSlug(), existingToggle);
            }

            toggleStorage.removeAll();

            for (ConnectReleaseToggleRecord incomingToggle : incomingToggles) {
                toggleStorage.write(incomingToggle);

                ConnectReleaseToggleRecord matchingToggle = existingTogglesMap.get(incomingToggle.getSlug());
                if (matchingToggle == null || matchingToggle.getActive() != incomingToggle.getActive()) {
                    togglesChanged = true;
                }
            }

            ConnectDatabaseHelper.connectDatabase.setTransactionSuccessful();
        } finally {
            ConnectDatabaseHelper.connectDatabase.endTransaction();
        }

        if (togglesChanged) {
            FirebaseAnalyticsUtil.reportPersonalIdReleaseTogglesChanged(incomingToggles);
        }
    }

    public static List<ConnectReleaseToggleRecord> getReleaseToggles() {
        SqlStorage<ConnectReleaseToggleRecord> toggleStorage =
                ConnectDatabaseHelper.getConnectStorage(ConnectReleaseToggleRecord.class);

        return toggleStorage.getRecordsForValues(new String[]{}, new Object[]{});
    }
}
