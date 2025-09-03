package org.commcare.connect.database;

import android.content.Context;

import org.commcare.android.database.connect.models.ConnectJobPaymentRecord;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;

import java.util.Collections;
import java.util.List;

public class NotificationRecordsHelper {
    public static List<ConnectMessagingMessageRecord> getMessagingMessagesForNotification(Context context, String notificationId) {
        if (notificationId == null) {
            return Collections.emptyList();
        } else {
            return ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingMessageRecord.class).getRecordsForValues(new String[]{ConnectMessagingMessageRecord.META_MESSAGE_NOTIFICATION_ID}, new Object[]{notificationId});
        }
    }

    public static List<ConnectMessagingChannelRecord> getMessagingChannelsForNotification(Context context, String notificationId) {
        if (notificationId == null) {
            return Collections.emptyList();
        } else {
            return ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingChannelRecord.class).getRecordsForValues(new String[]{ConnectMessagingChannelRecord.META_CHANNEL_NOTIFICATION_ID}, new Object[]{notificationId});
        }
    }

    public static List<ConnectJobPaymentRecord> getConnectJobPaymentRecordForNotification(Context context, String notificationId) {
        if (notificationId == null) {
            return Collections.emptyList();
        } else {
            return ConnectDatabaseHelper.getConnectStorage(context, ConnectJobPaymentRecord.class).getRecordsForValues(new String[]{ConnectJobPaymentRecord.META_NOTIFICATION_ID}, new Object[]{notificationId});
        }
    }
}
