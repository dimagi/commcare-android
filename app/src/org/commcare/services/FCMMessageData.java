package org.commcare.services;

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

/**
 * This class is to facilitate handling the FCM Message Data object. It should contain all the
 * necessary checks and transformations
 */
public class FCMMessageData implements Externalizable{
    private CommCareFirebaseMessagingService.ActionTypes action;
    private String username;
    private String domain;
    private DateTime creationTime;

    public FCMMessageData(Map<String, String> payloadData){
        this.action = getActionType(payloadData.get("action"));
        this.username = payloadData.get("username");
        this.domain = payloadData.get("domain");
        this.creationTime = convertISO8601ToDateTime(payloadData.get("created_at"));
    }


    public FCMMessageData(){}

    public String getUsername() {
        return username;
    }

    public String getDomain() {
        return domain;
    }

    public DateTime getCreationTime() {
        return creationTime;
    }

    public CommCareFirebaseMessagingService.ActionTypes getAction() {
        return action;
    }

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

    private CommCareFirebaseMessagingService.ActionTypes getActionType(String action) {
        if (action == null) {
            return CommCareFirebaseMessagingService.ActionTypes.INVALID;
        }

        switch (action.toUpperCase()) {
            case "SYNC" -> {
                return CommCareFirebaseMessagingService.ActionTypes.SYNC;
            }
            default -> {
                return CommCareFirebaseMessagingService.ActionTypes.INVALID;
            }
        }
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf) throws IOException, DeserializationException {
        action = CommCareFirebaseMessagingService.ActionTypes.valueOf(ExtUtil.readString(in));
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
}

