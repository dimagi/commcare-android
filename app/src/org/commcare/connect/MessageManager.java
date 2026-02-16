package org.commcare.connect;

import android.content.Context;
import android.util.Log;
import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.connectId.PersonalIdApiHandler;
import org.commcare.utils.PushNotificationApiHelper;
import org.javarosa.core.services.Logger;

import java.util.List;
import java.util.Map;

public class MessageManager {

    public static ConnectMessagingChannelRecord handleReceivedChannel(Context context, Map<String, String> payloadData) {
        ConnectMessagingChannelRecord channel = ConnectMessagingChannelRecord.fromMessagePayload(payloadData);
        ConnectMessagingDatabaseHelper.storeMessagingChannel(context, channel);

        return channel;
    }

    public static void retrieveMessages(Context context, ConnectActivityCompleteListener listener) {
        PushNotificationApiHelper.INSTANCE.retrieveLatestPushNotificationsWithCallback(context, listener);
    }

    public static void updateChannelConsent(Context context, ConnectMessagingChannelRecord channel,
                                            ConnectActivityCompleteListener listener) {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
        new PersonalIdApiHandler<Boolean>() {
            @Override
            public void onSuccess(Boolean success) {
                ConnectMessagingDatabaseHelper.storeMessagingChannel(context, channel);

                if (channel.getConsented()) {
                    getChannelEncryptionKey(context, channel, listener);
                } else {
                    listener.connectActivityComplete(true,null);
                }
            }

            @Override
            public void onFailure(PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
                listener.connectActivityComplete(false,null);
            }
        }.updateChannelConsent(context, user,channel);
    }

    public static void getChannelEncryptionKey(Context context, ConnectMessagingChannelRecord channel,
                                               ConnectActivityCompleteListener listener) {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
        new PersonalIdApiHandler<Boolean>() {
            @Override
            public void onSuccess(Boolean success) {
                listener.connectActivityComplete(success,null);
            }

            @Override
            public void onFailure(PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
                Logger.log("Messaging", "Failed to retrieve encryption key: " + (t!=null ? t.getMessage():""));
                listener.connectActivityComplete(false,null);
            }
        }.retrieveChannelEncryptionKey(context, user,channel);
    }

    public static void sendUnsentMessages(Context context) {
        List<ConnectMessagingMessageRecord> messages = ConnectMessagingDatabaseHelper.getMessagingMessagesAll(context);
        for (ConnectMessagingMessageRecord message : messages) {
            if (message.getIsOutgoing() && !message.getConfirmed()) {
                sendMessage(context, message, (success,error) -> {
                    Log.d("Check", Boolean.toString(success));
                });
                break;
            }
        }
    }

    public static void sendMessage(Context context, ConnectMessagingMessageRecord message,
                                   ConnectActivityCompleteListener listener) {
        ConnectMessagingChannelRecord channel = ConnectMessagingDatabaseHelper.getMessagingChannel(context, message.getChannelId());

        if (!channel.getKey().isEmpty()) {
            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);

            new PersonalIdApiHandler<Boolean>() {
                @Override
                public void onSuccess(Boolean success) {
                    message.setConfirmed(true);
                    ConnectMessagingDatabaseHelper.storeMessagingMessage(context, message);
                    listener.connectActivityComplete(true,null);
                }

                @Override
                public void onFailure(PersonalIdOrConnectApiErrorCodes failureCode, Throwable t) {
                    listener.connectActivityComplete(false,null);
                }
            }.sendMessagingMessage(context, user,message,channel);
        } else {
            Logger.log("Messaging", "Tried to send message but no encryption key");
        }
    }
}
