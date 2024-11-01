package org.commcare.connect;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.javarosa.core.io.StreamsUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MessageManager {

    private static MessageManager manager = null;

    public static MessageManager getInstance() {
        if (manager == null) {
            manager = new MessageManager();
        }

        return manager;
    }

    public static void retrieveMessages(Context context, ConnectManager.ConnectActivityCompleteListener listener) {
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData)  {
                try {
                    String responseAsString = new String(
                            StreamsUtil.inputStreamToByteArray(responseData));
                    Log.e("DEBUG_TESTING", "processSuccess: " + responseAsString);
                    List<ConnectMessagingChannelRecord> channels = new ArrayList<>();
                    List<ConnectMessagingMessageRecord> messages = new ArrayList<>();
                    if(responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        JSONArray channelsJson = json.getJSONArray("channels");
                        for (int i = 0; i < channelsJson.length(); i++) {
                            JSONObject obj = (JSONObject) channelsJson.get(i);
                            ConnectMessagingChannelRecord channel = ConnectMessagingChannelRecord.fromJson(obj);
                            channels.add(channel);
                        }

                        JSONArray messagesJson = json.getJSONArray("messages");
                        List<ConnectMessagingChannelRecord> existingChannels = ConnectDatabaseHelper.getMessagingChannels(context);
                        for (int i = 0; i < messagesJson.length(); i++) {
                            JSONObject obj = (JSONObject) messagesJson.get(i);
                            ConnectMessagingMessageRecord message = ConnectMessagingMessageRecord.fromJson(obj, existingChannels);
                            if(message != null) {
                                messages.add(message);
                            }
                        }
                    }

                    ConnectDatabaseHelper.storeMessagingChannels(context, channels, true);
                    ConnectDatabaseHelper.storeMessagingMessages(context, messages, false);

                    if(messages.size() > 0) {
                        MessageManager.updateReceivedMessages(context, success -> {
                            Log.d("Check", Boolean.toString(success));
                        });
                    }

                    listener.connectActivityComplete(true);
                } catch(Exception e) {
                    Log.e("Error", "Oops", e);
                    listener.connectActivityComplete(false);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Log.e("DEBUG_TESTING", "processFailure: " + responseCode);
                listener.connectActivityComplete(false);

                String message = "";
                if (responseCode > 0) {
                    message = String.format(Locale.getDefault(), "(%d)", responseCode);
                } else if (e != null) {
                    message = e.toString();
                }
            }

            @Override
            public void processNetworkFailure() {
                Log.e("DEBUG_TESTING", "processNetworkFailure: ");
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                Log.e("DEBUG_TESTING", "processOldApiError: ");
                listener.connectActivityComplete(false);
            }
        };

        ConnectUserRecord user = ConnectManager.getUser(context);
        boolean isBusy = !ApiConnectId.retrieveMessages(context, user.getUserId(), user.getPassword(), callback);

        if (isBusy) {
            Toast.makeText(context, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void updateChannelConsent(Context context, ConnectMessagingChannelRecord channel,
                                            ConnectManager.ConnectActivityCompleteListener listener) {
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData)  {
                try {
                    String responseAsString = new String(
                            StreamsUtil.inputStreamToByteArray(responseData));
                    Log.e("DEBUG_TESTING", "processSuccess: " + responseAsString);

                    ConnectDatabaseHelper.storeMessagingChannel(context, channel);

                    if(channel.getConsented()) {
                        getChannelEncryptionKey(context, channel, listener);
                    } else {
                        listener.connectActivityComplete(true);
                    }
                } catch(Exception e) {
                    Log.e("Error", "Oops", e);
                    listener.connectActivityComplete(false);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Log.e("DEBUG_TESTING", "processFailure: " + responseCode);
                //listener.connectActivityComplete(false);
                getChannelEncryptionKey(context, channel, listener);

                String message = "";
                if (responseCode > 0) {
                    message = String.format(Locale.getDefault(), "(%d)", responseCode);
                } else if (e != null) {
                    message = e.toString();
                }
            }

            @Override
            public void processNetworkFailure() {
                Log.e("DEBUG_TESTING", "processNetworkFailure: ");
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                Log.e("DEBUG_TESTING", "processOldApiError: ");
                listener.connectActivityComplete(false);
            }
        };

        ConnectUserRecord user = ConnectManager.getUser(context);
        boolean isBusy = !ApiConnectId.updateChannelConsent(context, user.getUserId(), user.getPassword(),
                channel.getChannelId(), channel.getConsented(), callback);

        if (isBusy) {
            Toast.makeText(context, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void getChannelEncryptionKey(Context context, ConnectMessagingChannelRecord channel,
                                               ConnectManager.ConnectActivityCompleteListener listener) {
        ApiConnectId.retrieveChannelEncryptionKey(context, channel.getChannelId(), channel.getKeyUrl(),
                new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(
                            StreamsUtil.inputStreamToByteArray(responseData));
                    Log.e("DEBUG_TESTING", "processSuccess: " + responseAsString);

                    if(responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        channel.setKey(json.getString("key"));
                        ConnectDatabaseHelper.storeMessagingChannel(context, channel);
                    }

                    listener.connectActivityComplete(true);
                } catch(Exception e) {
                    Log.e("Error", "Oops", e);
                    listener.connectActivityComplete(false);
                }
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                Log.d("DEBUG", "Chcek");
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                Log.d("DEBUG", "Chcek");
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                Log.d("DEBUG", "Chcek");
                listener.connectActivityComplete(false);
            }
        });
    }

    public static void updateReceivedMessages(Context context, ConnectManager.ConnectActivityCompleteListener listener) {
        List<ConnectMessagingMessageRecord> messages = ConnectDatabaseHelper.getMessagingMessagesAll(context);
        List<ConnectMessagingMessageRecord> unsent = new ArrayList<>();
        List<String> unsentIds = new ArrayList<>();
        for(ConnectMessagingMessageRecord message : messages) {
            if(!message.getIsOutgoing() && !message.getConfirmed()) {
                unsent.add(message);
                unsentIds.add(message.getMessageId());
            }
        }

        if(unsentIds.size() > 0) {
            ConnectUserRecord user = ConnectManager.getUser(context);
            ApiConnectId.confirmReceivedMessages(context, user.getUserId(), user.getPassword(), unsentIds, new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    for(ConnectMessagingMessageRecord message : unsent) {
                        message.setConfirmed(true);
                        ConnectDatabaseHelper.storeMessagingMessage(context, message);
                    }
                    listener.connectActivityComplete(true);
                }

                @Override
                public void processFailure(int responseCode, IOException e) {
                    listener.connectActivityComplete(false);
                }

                @Override
                public void processNetworkFailure() {
                    listener.connectActivityComplete(false);
                }

                @Override
                public void processOldApiError() {
                    listener.connectActivityComplete(false);
                }
            });
        }
    }

    public static void sendUnsentMessages(Context context) {
        List<ConnectMessagingMessageRecord> messages = ConnectDatabaseHelper.getMessagingMessagesAll(context);
        for(ConnectMessagingMessageRecord message : messages) {
            if(message.getIsOutgoing() && !message.getConfirmed()) {
                //sendMessage(context, message, success -> {
                //    Log.d("Check", Boolean.toString(success));
                //});
                break;
            }
        }
    }

    public static void sendMessage(Context context, ConnectMessagingMessageRecord message,
                                   ConnectManager.ConnectActivityCompleteListener listener) {
        ConnectDatabaseHelper.storeMessagingMessage(context, message);
        ConnectMessagingChannelRecord channel = ConnectDatabaseHelper.getMessagingChannel(context, message.getChannelId());

        ConnectUserRecord user = ConnectManager.getUser(context);
        ApiConnectId.sendMessagingMessage(context, user.getUserId(), user.getPassword(), message, channel.getKey(), new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                message.setConfirmed(true);
                ConnectDatabaseHelper.storeMessagingMessage(context, message);
                listener.connectActivityComplete(true);
            }

            @Override
            public void processFailure(int responseCode, IOException e) {
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                listener.connectActivityComplete(false);
            }
        });
    }
}
