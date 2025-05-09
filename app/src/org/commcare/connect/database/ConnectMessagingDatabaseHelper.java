package org.commcare.connect.database;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.style.ImageSpan;

import org.commcare.android.database.connect.models.ConnectMessagingChannelRecord;
import org.commcare.android.database.connect.models.ConnectMessagingMessageRecord;
import org.commcare.dalvik.R;
import org.commcare.models.database.SqlStorage;
import org.commcare.utils.DimensionUtils;
import androidx.core.content.ContextCompat;

import java.util.List;
import java.util.Vector;

import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;

public class ConnectMessagingDatabaseHelper {
    public static List<ConnectMessagingChannelRecord> getMessagingChannels(Context context) {
        List<ConnectMessagingChannelRecord> channels = ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingChannelRecord.class)
                .getRecordsForValues(new String[]{}, new Object[]{});

        for(ConnectMessagingMessageRecord message : getMessagingMessagesAll(context)) {
            for(ConnectMessagingChannelRecord searchChannel : channels) {
                if(message.getChannelId().equals(searchChannel.getChannelId())) {
                    searchChannel.getMessages().add(message);
                    break;
                }
            }
        }

        for(ConnectMessagingChannelRecord channel : channels) {
            List<ConnectMessagingMessageRecord> messages = channel.getMessages();
            ConnectMessagingMessageRecord lastMessage = messages.size() > 0 ?
                    messages.get(messages.size() - 1) : null;
            SpannableString preview;
            if(!channel.getConsented()) {
                preview = new SpannableString(context.getString(R.string.connect_messaging_channel_list_unconsented));
            } else if(lastMessage != null) {

                String trimmed = lastMessage.getMessage().split("\n")[0];
                int maxLength = 25;
                if(trimmed.length() > maxLength) {
                    trimmed = trimmed.substring(0, maxLength - 3) + "...";
                }
                preview = new SpannableString(lastMessage.getIsOutgoing()? "  "+trimmed:trimmed);
                if(lastMessage.getIsOutgoing()){
                    Drawable drawable = lastMessage.getConfirmed() ? ContextCompat.getDrawable(context, R.drawable.ic_connect_message_read) : ContextCompat.getDrawable(context, R.drawable.ic_connect_message_unread);
                    float lineHeight = DimensionUtils.INSTANCE.convertDpToPixel(14);
                    drawable.setBounds(0,0,(int) lineHeight, (int) lineHeight);
                    preview.setSpan(new ImageSpan(drawable), 0, 1, SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                preview = new SpannableString("");
            }

            channel.setPreview(preview);
        }

        return channels;
    }

    public static ConnectMessagingChannelRecord getMessagingChannel(Context context, String channelId) {
        List<ConnectMessagingChannelRecord> channels = ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingChannelRecord.class)
                .getRecordsForValues(new String[]{ConnectMessagingChannelRecord.META_CHANNEL_ID},
                        new Object[]{channelId});

        if(channels.size() > 0) {
            return channels.get(0);
        }

        return null;
    }

    public static void storeMessagingChannel(Context context, ConnectMessagingChannelRecord channel) {
        ConnectMessagingChannelRecord existing = getMessagingChannel(context, channel.getChannelId());
        if(existing != null) {
            channel.setID(existing.getID());
        }

        ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingChannelRecord.class).write(channel);
    }

    public static void storeMessagingChannels(Context context, List<ConnectMessagingChannelRecord> channels, boolean pruneMissing) {
        SqlStorage<ConnectMessagingChannelRecord> storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingChannelRecord.class);

        List<ConnectMessagingChannelRecord> existingList = getMessagingChannels(context);

        //Delete payments that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectMessagingChannelRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectMessagingChannelRecord incoming : channels) {
                if (existing.getChannelId().equals(incoming.getChannelId())) {
                    incoming.setID(existing.getID());

                    incoming.setChannelCreated(existing.getChannelCreated());

                    if(!incoming.getAnsweredConsent()) {
                        incoming.setAnsweredConsent(existing.getAnsweredConsent());
                    }

                    if(existing.getKey().length() > 0) {
                        incoming.setKey(existing.getKey());
                    }

                    stillExists = true;
                    break;
                }
            }

            if (!stillExists && pruneMissing) {
                //Mark the delivery for deletion
                //Remember the ID so we can delete them all at once after the loop
                recordIdsToDelete.add(existing.getID());
            }
        }

        if (pruneMissing) {
            storage.removeAll(recordIdsToDelete);
        }

        //Now insert/update deliveries
        for (ConnectMessagingChannelRecord incomingRecord : channels) {
            storage.write(incomingRecord);
        }
    }

    public static List<ConnectMessagingMessageRecord> getMessagingMessagesAll(Context context) {
        return ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingMessageRecord.class)
                .getRecordsForValues(new String[]{}, new Object[]{});
    }

    public static List<ConnectMessagingMessageRecord> getMessagingMessagesForChannel(Context context, String channelId) {
        return ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingMessageRecord.class)
                .getRecordsForValues(new String[]{ ConnectMessagingMessageRecord.META_MESSAGE_CHANNEL_ID }, new Object[]{channelId});
    }

    public static List<ConnectMessagingMessageRecord> getUnviewedMessages(Context context) {
        return ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingMessageRecord.class)
                .getRecordsForValues(new String[]{ ConnectMessagingMessageRecord.META_MESSAGE_USER_VIEWED }, new Object[]{false});
    }

    public static void storeMessagingMessage(Context context, ConnectMessagingMessageRecord message) {
        SqlStorage<ConnectMessagingMessageRecord> storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingMessageRecord.class);

        List<ConnectMessagingMessageRecord> existingList = getMessagingMessagesForChannel(context, message.getChannelId());
        for (ConnectMessagingMessageRecord existing : existingList) {
            if(existing.getMessageId().equals(message.getMessageId())) {
                message.setID(existing.getID());
                break;
            }
        }

        storage.write(message);
    }

    public static void storeMessagingMessages(Context context, List<ConnectMessagingMessageRecord> messages, boolean pruneMissing) {
        SqlStorage<ConnectMessagingMessageRecord> storage = ConnectDatabaseHelper.getConnectStorage(context, ConnectMessagingMessageRecord.class);

        List<ConnectMessagingMessageRecord> existingList = getMessagingMessagesAll(context);

        //Delete payments that are no longer available
        Vector<Integer> recordIdsToDelete = new Vector<>();
        for (ConnectMessagingMessageRecord existing : existingList) {
            boolean stillExists = false;
            for (ConnectMessagingMessageRecord incoming : messages) {
                if (existing.getMessageId().equals(incoming.getMessageId())) {
                    incoming.setID(existing.getID());
                    stillExists = true;
                    break;
                }
            }

            if (!stillExists && pruneMissing) {
                //Mark the delivery for deletion
                //Remember the ID so we can delete them all at once after the loop
                recordIdsToDelete.add(existing.getID());
            }
        }

        if (pruneMissing) {
            storage.removeAll(recordIdsToDelete);
        }

        //Now insert/update deliveries
        for (ConnectMessagingMessageRecord incomingRecord : messages) {
            storage.write(incomingRecord);
        }
    }
}
