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
public class FCMMessageData implements Externalizable{
    private FirebaseMessagingUtil.ActionTypes actionType;
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

    /**
     * Constructor for FCMMessageData
     * @param payloadData
     */
    public FCMMessageData(Map<String, String> payloadData){
        this.payloadData = payloadData;
        actionType = getActionType(payloadData.get("action"));
        username = payloadData.get("username");
        domain = payloadData.get("domain");
        creationTime = convertISO8601ToDateTime(payloadData.get("created_at"));
        notificationTitle = payloadData.get("title");
        notificationText = payloadData.get("body");
        action = payloadData.get("action");
        priority = NotificationCompat.PRIORITY_HIGH;
        notificationChannel = CommCareNoficationManager.NOTIFICATION_CHANNEL_PUSH_NOTIFICATIONS_ID;
        smallIcon = R.drawable.commcare_actionbar_logo;
    }
    
    public FCMMessageData(){}

    /**
     * Getter for username
     * @return
     */
    public String getUsername() {
        return username;
    }

    /**
     * Getter for domain
     * @return
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Getter for creation time
     * @return
     */
    public DateTime getCreationTime() {
        return creationTime;
    }

    /**
     * Getter for action type
     * @return
     */
    public FirebaseMessagingUtil.ActionTypes getActionType() {
        return actionType;
    }

    /**
     * Convert ISO 8601 string to DateTime object
     * @param timeInISO8601
     * @return
     */
    private DateTime convertISO8601ToDateTime(String timeInISO8601) {
        if (timeInISO8601 == null){
            return new DateTime();
        }
        try {
            return new DateTime(timeInISO8601);
        } catch (Exception e) {
            Logger.exception("Incorrect Date format, expected in ISO 8601: ", e);
            return new DateTime();
        }
    }

    /**
     * Get action type based on action
     * @param action
     * @return
     */
    private FirebaseMessagingUtil.ActionTypes getActionType(String action) {
        if (action == null) {
            return FirebaseMessagingUtil.ActionTypes.INVALID;
        }

        switch (action.toUpperCase()) {
            case "SYNC" -> {
                return FirebaseMessagingUtil.ActionTypes.SYNC;
            }
            default -> {
                return FirebaseMessagingUtil.ActionTypes.INVALID;
            }
        }
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        actionType = FirebaseMessagingUtil.ActionTypes.valueOf(ExtUtil.readString(in));
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

    /**
     * Getter for notification title
     * @return
     */
    public String getNotificationTitle() {
        return notificationTitle;
    }

    /**
     * Setter for notification title
     * @param notificationTitle
     */
    public void setNotificationTitle(String notificationTitle) {
        this.notificationTitle = notificationTitle;
    }

    /**
     * Getter for notification text
     * @return
     */
    public String getNotificationText() {
        return notificationText;
    }

    /**
     * Setter for notification text
     * @param notificationText
     */
    public void setNotificationText(String notificationText) {
        this.notificationText = notificationText;
    }

    /**
     * Getter for priority
     * @return
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Setter for priority
     * @param priority
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Getter for large icon
     * @return
     */
    public Bitmap getLargeIcon() {
        return largeIcon;
    }

    /**
     * Setter for large icon
     * @param largeIcon
     */
    public void setLargeIcon(Bitmap largeIcon) {
        this.largeIcon = largeIcon;
    }

    /**
     * Getter for small icon
     * @return
     */
    public int getSmallIcon() {
        return smallIcon;
    }

    /**
     * Setter for small icon
     * @param smallIcon
     */
    public void setSmallIcon( int smallIcon) {
        this.smallIcon = smallIcon;
    }

    /**
     * Getter for action
     * @param action
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * Getter for action
     * @return
     */
    public String getAction() {
        return action;
    }

    /**
     * Getter for notification channel
     * @return
     */
    public String getNotificationChannel() {
        return notificationChannel;
    }

    /**
     * Setter for notification channel
     * @param notificationChannel
     */
    public void setNotificationChannel(String notificationChannel) {
        this.notificationChannel = notificationChannel;
    }

    /**
     * Getter for payload data
     * @return
     */
    public Map<String, String> getPayloadData() {
        return payloadData;
    }
}

