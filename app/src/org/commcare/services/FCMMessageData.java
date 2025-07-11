package org.commcare.services;

import android.graphics.Bitmap;

import org.commcare.CommCareNoficationManager;
import org.commcare.dalvik.R;
import org.commcare.utils.FirebaseMessagingUtil;
import org.javarosa.core.services.Logger;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.Externalizable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.joda.time.DateTime;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;

import androidx.core.app.NotificationCompat;

/**
 * This class is to facilitate handling the FCM Message Data object. It should contain all the
 * necessary checks and transformations
 */
public class FCMMessageData implements Externalizable {
    private ActionTypes actionType;
    private String username;
    private String domain;
    private DateTime creationTime;
    private String notificationTitle;
    private String notificationText;
    private int priority;
    private Bitmap largeIcon;
    private int smallIcon;
    private String action;
    private String notificationChannel;
    private Map<String, String> payloadData;


    public static String NOTIFICATION_TITLE = "title";
    public static String NOTIFICATION_BODY = "body";


    /**
     * Action Type for data syncer
     */
    public enum ActionTypes {
        SYNC,
        INVALID
    }

    public FCMMessageData(Map<String, String> payloadData) {
        this.payloadData = payloadData;
        actionType = getActionType(payloadData.get("action"));
        username = payloadData.get("username");
        domain = payloadData.get("domain");
        creationTime = convertISO8601ToDateTime(payloadData.get("created_at"));
        notificationTitle = payloadData.get(NOTIFICATION_TITLE);
        notificationText = payloadData.get(NOTIFICATION_BODY);
        action = payloadData.get("action");
        priority = NotificationCompat.PRIORITY_HIGH;
        notificationChannel = CommCareNoficationManager.NOTIFICATION_CHANNEL_PUSH_NOTIFICATIONS_ID;
        smallIcon = R.drawable.commcare_actionbar_logo;
    }

    public FCMMessageData() {
    }

    public String getUsername() {
        return username;
    }

    public String getDomain() {
        return domain;
    }

    public DateTime getCreationTime() {
        return creationTime;
    }

    public ActionTypes getActionType() {
        return actionType;
    }

    private DateTime convertISO8601ToDateTime(String timeInISO8601) {
        if (timeInISO8601 == null) {
            return new DateTime();
        }
        try {
            return new DateTime(timeInISO8601);
        } catch (Exception e) {
            Logger.exception("Incorrect Date format, expected in ISO 8601: ", e);
            return new DateTime();
        }
    }

    private ActionTypes getActionType(String action) {
        if (action == null) {
            return ActionTypes.INVALID;
        }

        switch (action.toUpperCase()) {
            case "SYNC" -> {
                return ActionTypes.SYNC;
            }
            default -> {
                return ActionTypes.INVALID;
            }
        }
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        actionType = ActionTypes.valueOf(ExtUtil.readString(in));
        username = ExtUtil.readString(in);
        domain = ExtUtil.readString(in);
        creationTime = new DateTime(ExtUtil.readLong(in));
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        ExtUtil.writeString(out, action.toString());
        ExtUtil.writeString(out, username);
        ExtUtil.writeString(out, domain);
        ExtUtil.writeNumeric(out, creationTime.getMillis());
    }

    public String getNotificationTitle() {
        return notificationTitle;
    }

    public void setNotificationTitle(String notificationTitle) {
        this.notificationTitle = notificationTitle;
    }

    public String getNotificationText() {
        return notificationText;
    }

    public void setNotificationText(String notificationText) {
        this.notificationText = notificationText;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Bitmap getLargeIcon() {
        return largeIcon;
    }

    public void setLargeIcon(Bitmap largeIcon) {
        this.largeIcon = largeIcon;
    }

    public int getSmallIcon() {
        return smallIcon;
    }

    public void setSmallIcon(int smallIcon) {
        this.smallIcon = smallIcon;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    public String getNotificationChannel() {
        return notificationChannel;
    }

    public void setNotificationChannel(String notificationChannel) {
        this.notificationChannel = notificationChannel;
    }

    public Map<String, String> getPayloadData() {
        return payloadData;
    }
}

