package org.commcare.connect;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.database.ConnectMessagingDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiPersonalId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.commcare.util.LogTypes;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.services.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;

public class MessageManager {

    public static ConnectMessagingChannelRecord handleReceivedChannel(Context context, Map<String, String> payloadData) {
        ConnectMessagingChannelRecord channel = ConnectMessagingChannelRecord.fromMessagePayload(payloadData);
        ConnectMessagingDatabaseHelper.storeMessagingChannel(context, channel);

        return channel;
    }

    public static void retrieveMessages(Context context, ConnectActivityCompleteListener listener) {
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try (InputStream in = responseData) {
                    String responseAsString = new String(StreamsUtil.inputStreamToByteArray(in));
                    List<ConnectMessagingChannelRecord> channels = new ArrayList<>();
                    List<ConnectMessagingMessageRecord> messages = new ArrayList<>();
                    if (responseAsString.length() > 0) {
                        JSONObject json = new JSONObject(responseAsString);
                        JSONArray channelsJson = json.getJSONArray("channels");
                        for (int i = 0; i < channelsJson.length(); i++) {
                            JSONObject obj = (JSONObject)channelsJson.get(i);
                            ConnectMessagingChannelRecord channel = ConnectMessagingChannelRecord.fromJson(obj);
                            channels.add(channel);
                        }

                        ConnectMessagingDatabaseHelper.storeMessagingChannels(context, channels, true);

                        for (ConnectMessagingChannelRecord channel : channels) {
                            if (channel.getConsented() && channel.getKey().length() == 0) {
                                getChannelEncryptionKey(context, channel, null);
                            }
                        }

                        JSONArray messagesJson = json.getJSONArray("messages");
                        List<ConnectMessagingChannelRecord> existingChannels = ConnectMessagingDatabaseHelper.getMessagingChannels(context);
                        for (int i = 0; i < messagesJson.length(); i++) {
                            JSONObject obj = (JSONObject)messagesJson.get(i);
                            ConnectMessagingMessageRecord message = ConnectMessagingMessageRecord.fromJson(obj, existingChannels);
                            if (message != null) {
                                messages.add(message);
                            }
                        }
                    }

                    ConnectMessagingDatabaseHelper.storeMessagingMessages(context, messages, false);


                    if (messages.size() > 0) {
                        MessageManager.updateReceivedMessages(context, success -> {
                            //Do nothing
                        });
                    }

                    listener.connectActivityComplete(true);
                } catch (Exception e) {
                    listener.connectActivityComplete(false);
                }
            }

            @Override
            public void processFailure(int responseCode, @Nullable InputStream errorResponse, String url) {
                listener.connectActivityComplete(false);
            }

            @Override
            public void processNetworkFailure() {
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenUnavailableError() {
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenRequestDeniedError() {
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                listener.connectActivityComplete(false);
            }
        };

        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
        ApiPersonalId.retrieveMessages(context, user.getUserId(), user.getPassword(), callback);
    }

    public static void updateChannelConsent(Context context, ConnectMessagingChannelRecord channel,
                                            ConnectActivityCompleteListener listener) {
        IApiCallback callback = new IApiCallback() {
            @Override
            public void processSuccess(int responseCode, InputStream responseData) {
                try {
                    String responseAsString = new String(
                            StreamsUtil.inputStreamToByteArray(responseData));

                    ConnectMessagingDatabaseHelper.storeMessagingChannel(context, channel);

                    if (channel.getConsented()) {
                        getChannelEncryptionKey(context, channel, listener);
                    } else {
                        listener.connectActivityComplete(true);
                    }
                } catch (Exception e) {
                    listener.connectActivityComplete(false);
                }
            }

            @Override

            public void processFailure(int responseCode, @Nullable InputStream errorResponse, String url) {
                //listener.connectActivityComplete(false);
                getChannelEncryptionKey(context, channel, listener);
            }

            @Override
            public void processNetworkFailure() {
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenUnavailableError() {
                listener.connectActivityComplete(false);
            }

            @Override
            public void processTokenRequestDeniedError() {
                listener.connectActivityComplete(false);
            }

            @Override
            public void processOldApiError() {
                listener.connectActivityComplete(false);
            }
        };

        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
        boolean isBusy = !ApiPersonalId.updateChannelConsent(context, user.getUserId(), user.getPassword(),
                channel.getChannelId(), channel.getConsented(), callback);

        if (isBusy) {
            Toast.makeText(context, R.string.busy_message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void getChannelEncryptionKey(Context context, ConnectMessagingChannelRecord channel,
                                               ConnectActivityCompleteListener listener) {
        ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
        ApiPersonalId.retrieveChannelEncryptionKey(context, user, channel.getChannelId(), channel.getKeyUrl(),
                new IApiCallback() {
                    @Override
                    public void processSuccess(int responseCode, InputStream responseData) {
                        try (InputStream in = responseData) {
                            ApiPersonalId.handleReceivedChannelEncryptionKey(context, in, channel);
                            if (listener != null) {
                                listener.connectActivityComplete(true);
                            }
                        } catch (IOException e) {
                            Logger.exception("Exception occurred while handling received encryption key", e );
                        }
                    }

                    @Override
                    public void processFailure(int responseCode, @Nullable InputStream errorResponse, String url) {
                        if (listener != null) {
                            listener.connectActivityComplete(false);
                        }
                    }

                    @Override
                    public void processNetworkFailure() {
                        if (listener != null) {
                            listener.connectActivityComplete(false);
                        }
                    }

                    @Override
                    public void processTokenUnavailableError() {
                        if (listener != null) {
                            listener.connectActivityComplete(false);
                        }
                    }

                    @Override
                    public void processTokenRequestDeniedError() {
                        if (listener != null) {
                            listener.connectActivityComplete(false);
                        }
                    }

                    @Override
                    public void processOldApiError() {
                        if (listener != null) {
                            listener.connectActivityComplete(false);
                        }
                    }
                });
    }

    public static void updateReceivedMessages(Context context, ConnectActivityCompleteListener listener) {
        List<ConnectMessagingMessageRecord> messages = ConnectMessagingDatabaseHelper.getMessagingMessagesAll(context);
        List<ConnectMessagingMessageRecord> unsent = new ArrayList<>();
        List<String> unsentIds = new ArrayList<>();
        for (ConnectMessagingMessageRecord message : messages) {
            if (!message.getIsOutgoing() && !message.getConfirmed()) {
                unsent.add(message);
                unsentIds.add(message.getMessageId());
            }
        }

        if (unsentIds.size() > 0) {
            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
            ApiPersonalId.confirmReceivedMessages(context, user.getUserId(), user.getPassword(), unsentIds, new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    for (ConnectMessagingMessageRecord message : unsent) {
                        message.setConfirmed(true);
                        ConnectMessagingDatabaseHelper.storeMessagingMessage(context, message);
                    }
                    listener.connectActivityComplete(true);
                }

                @Override
                public void processFailure(int responseCode, @Nullable InputStream errorResponse, String url) {
                    listener.connectActivityComplete(false);
                }

                @Override
                public void processNetworkFailure() {
                    listener.connectActivityComplete(false);
                }

                @Override
                public void processTokenUnavailableError() {
                    listener.connectActivityComplete(false);
                }

                @Override
                public void processTokenRequestDeniedError() {
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
        List<ConnectMessagingMessageRecord> messages = ConnectMessagingDatabaseHelper.getMessagingMessagesAll(context);
        for (ConnectMessagingMessageRecord message : messages) {
            if (message.getIsOutgoing() && !message.getConfirmed()) {
                sendMessage(context, message, success -> {
                    Log.d("Check", Boolean.toString(success));
                });
                break;
            }
        }
    }

    public static void sendMessage(Context context, ConnectMessagingMessageRecord message,
                                   ConnectActivityCompleteListener listener) {
        ConnectMessagingChannelRecord channel = ConnectMessagingDatabaseHelper.getMessagingChannel(context, message.getChannelId());

        if (channel.getKey().length() > 0) {
            ConnectUserRecord user = ConnectUserDatabaseUtil.getUser(context);
            ApiPersonalId.sendMessagingMessage(context, user.getUserId(), user.getPassword(), message, channel.getKey(), new IApiCallback() {
                @Override
                public void processSuccess(int responseCode, InputStream responseData) {
                    message.setConfirmed(true);
                    ConnectMessagingDatabaseHelper.storeMessagingMessage(context, message);
                    listener.connectActivityComplete(true);
                }

                @Override
                public void processFailure(int responseCode, @Nullable InputStream errorResponse, String url) {
                    listener.connectActivityComplete(false);
                }

                @Override
                public void processNetworkFailure() {
                    listener.connectActivityComplete(false);
                }

                @Override
                public void processTokenUnavailableError() {
                    listener.connectActivityComplete(false);
                }

                @Override
                public void processTokenRequestDeniedError() {
                    listener.connectActivityComplete(false);
                }

                @Override
                public void processOldApiError() {
                    listener.connectActivityComplete(false);
                }
            });
        } else {
            Logger.log("Messaging", "Tried to send message but no encryption key");
        }
    }
}
