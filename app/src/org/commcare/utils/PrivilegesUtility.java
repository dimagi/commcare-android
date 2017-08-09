package org.commcare.utils;

import org.commcare.modern.util.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Helper methods for privilege data
 * <p>
 * Created by ctsims on 8/7/2017.
 */

public class PrivilegesUtility {
    private String authorityPublicKeyString;

    public PrivilegesUtility(String authorityPublicKeyString) {
        this.authorityPublicKeyString = authorityPublicKeyString;
    }

    /**
     * Process an incoming payload JSON string and returns any privileges that were activated.
     * <p>
     * If processing is not possible, a typed exception will be thrown.
     *
     * @return Pair<Username, PrivilegeList> containing a list of privileges and the user tag
     * that they  should be activated for.
     */
    public Pair<String, String[]> processPrivilegePayloadForActivatedPrivileges
    (String privilegePayloadJson) throws PrivilagePayloadException {
        try {
            JSONObject obj = new JSONObject(privilegePayloadJson);
            int version = getPayloadVersion(obj);

            switch (version) {
                case 1:
                    return processAndValidateV1Payload(obj);
                case 2:
                    return processAndValidateV2Payload(obj);
                default:
                    throw new UnrecognizedPayloadVersionException();
            }

        } catch (JSONException e) {
            throw new MalformedPayloadException(e);
        }
    }

    private int getPayloadVersion(JSONObject obj) throws UnrecognizedPayloadVersionException,
            JSONException {
        if (obj.has("version")) {
            return obj.getInt("version");
        } else {
            if (obj.has("flag")) {
                return 1;
            } else {
                throw new UnrecognizedPayloadVersionException();
            }
        }
    }

    /**
     * @return Pair<Username, PrivilegeList> containing a list of privileges and the user tag
     * that they  should be activated for.
     */
    private Pair<String, String[]> processAndValidateV2Payload(JSONObject obj) throws JSONException, PrivilagePayloadException {
        Pair<String[], String[]> payload = processV2Payload(obj);
        String username = payload.first[0];
        String signature = payload.first[1];
        String[] privileges = payload.second;
        if (validatePayloadSignature(getInputForSignatureValidationV2(privileges, username), signature)) {
            return new Pair(username, privileges);
        } else {
            throw new InvalidPrivilegeSignatureException("Signatures don't match");
        }
    }

    private boolean validatePayloadSignature(String inputString, String signature) throws InvalidPrivilegeSignatureException {
        try {
            byte[] signatureBytes = SigningUtil.getBytesFromString(signature);
            return SigningUtil.verifyMessageAndBytes(authorityPublicKeyString, inputString, signatureBytes) != null;
        } catch (Exception e) {
            throw new InvalidPrivilegeSignatureException(e);
        }

    }

    private String getInputForSignatureValidationV2(String[] flags, String username) {
        try {
            JSONObject usernameObject = new JSONObject();
            usernameObject.put("username", username);
            JSONArray flagsArray = new JSONArray();
            for (int i = 0; i < flags.length; ++i) {
                flagsArray.put(i, flags[i]);
            }

            JSONObject flagObject = new JSONObject();
            flagObject.put("flags", flagsArray);

            JSONArray array = new JSONArray();
            array.put(usernameObject);
            array.put(flagObject);
            return array.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /**
     * @return Pair<[username, signature], PrivilegeList> containing a list of privileges and the user tag
     * that they  should be activated for.
     */
    private Pair<String[], String[]> processV2Payload(JSONObject obj) throws JSONException, PrivilagePayloadException {
        String username = obj.getString("username");
        JSONArray array = obj.getJSONArray("flags");
        String[] permissions = new String[array.length()];
        for (int i = 0; i < array.length(); ++i) {
            permissions[i] = array.getString(i);
        }

        String signature = obj.getString("multiple_flags_signature");
        return new Pair<>(new String[]{username, signature}, permissions);

    }

    /**
     * @return Pair<Username, PrivilegeList> containing a list of privileges and the user tag
     * that they  should be activated for.
     */
    private Pair<String, String[]> processAndValidateV1Payload(JSONObject obj) throws JSONException, PrivilagePayloadException {
        String[] payload = processV1Payload(obj);
        String username = payload[1];
        String signature = payload[2];
        String privilege = payload[0];

        if (validatePayloadSignature(getInputForSignatureValidationV1(privilege, username), signature)) {
            return new Pair(username, new String[]{privilege});
        } else {
            throw new InvalidPrivilegeSignatureException("Signatures don't match");
        }
    }

    private String getInputForSignatureValidationV1(String privilege, String username) {
        try {
            JSONObject usernameObject = new JSONObject();
            usernameObject.put("username", username);
            JSONObject flagObject = new JSONObject();
            flagObject.put("flag", privilege);

            JSONArray array = new JSONArray();
            array.put(usernameObject);
            array.put(flagObject);
            return array.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /**
     * @return [permission, username, signature]
     */

    protected String[] processV1Payload(JSONObject obj) throws JSONException {
        String username = obj.getString("username");
        String flag = obj.getString("flag");
        String signature = obj.getString("signature");
        return new String[]{flag, username, signature};
    }

    public static class PrivilagePayloadException extends Exception {
        public PrivilagePayloadException() {

        }

        public PrivilagePayloadException(String msg) {
            super(msg);
        }

        public PrivilagePayloadException(Exception e) {
            super(e);
        }
    }

    public static class MalformedPayloadException extends PrivilagePayloadException {
        public MalformedPayloadException(Exception e) {
            super(e);
        }

    }

    public static class InvalidPrivilegeSignatureException extends PrivilagePayloadException {
        public InvalidPrivilegeSignatureException(String msg) {
            super(msg);
        }

        public InvalidPrivilegeSignatureException(Exception e) {
            super(e);
        }

    }

    public static class UnrecognizedPayloadVersionException extends PrivilagePayloadException {

    }

}
