package org.commcare.utils;

import org.commcare.modern.util.Pair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Helper methods for privilege data
 *
 * Created by ctsims on 8/7/2017.
 */

public class PrivilegesUtility {
    private String authorityPublicKeyString;

    public PrivilegesUtility(String authorityPublicKeyString) {
        this.authorityPublicKeyString = authorityPublicKeyString;
    }

    public Pair<String, String[]> processPrivilegePayloadForActivatedPrivileges
            (String privilegePayloadJson) throws PrivilagePayloadException{
        try {
            JSONObject obj = new JSONObject(privilegePayloadJson);
            int version;
            if(obj.has("version")) {
                version = obj.getInt("version");
            } else {
                if(obj.has("flag")) {
                    version = 1;
                } else {
                    throw new UnrecognizedPayloadVersionException();
                }
            }

            switch(version) {
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

    private Pair<String, String[]> processAndValidateV2Payload(JSONObject obj) throws JSONException, PrivilagePayloadException {
        Pair<String[], String[]> payload = processV2Payload(obj);
        if(validateV2PayloadSignature(payload.first[0], payload.first[1], payload.second)) {
            return new Pair(payload.first[0], payload.second);
        } else {
            throw new InvalidPrivilegeSignatureException("Signatures don't match");
        }
    }

    private boolean validateV2PayloadSignature(String username, String signature, String[] flags) throws JSONException, PrivilagePayloadException {
        try {
            byte[] signatureBytes = SigningUtil.getBytesFromString(signature);
            String expectedUnsignedValue = getV2SignatureInput(flags, username);
            return SigningUtil.verifyMessageAndBytes(authorityPublicKeyString, expectedUnsignedValue, signatureBytes) != null;
        } catch (Exception e) {
            throw new InvalidPrivilegeSignatureException(e);
        }
    }

    private String getV2SignatureInput(String[] flags, String username) {
        try {
            JSONObject usernameObject = new JSONObject();
            usernameObject.put("username", username);
            JSONArray flagsArray = new JSONArray();
            for(int i = 0 ; i < flags.length ; ++i) {
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

    private Pair<String[], String[]> processV2Payload(JSONObject obj) throws JSONException, PrivilagePayloadException {
        String username = obj.getString("username");
        JSONArray array = obj.getJSONArray("flags");
        String[] permissions = new String[array.length()];
        for(int i = 0 ; i < array.length(); ++i) {
            permissions[i] = array.getString(i);
        }

        String signature = obj.getString("multiple_flags_signature");
        return new Pair<>(new String[] {username, signature}, permissions);

    }

    private Pair<String, String[]> processAndValidateV1Payload(JSONObject obj) throws JSONException, PrivilagePayloadException {
        String[] payload = processV1Payload(obj);
        if(validateV1PayloadSignature(payload[0], payload[1], payload[2])) {
            return new Pair(payload[1], new String[] {payload[0]});
        } else {
            throw new InvalidPrivilegeSignatureException("Signatures don't match");
        }
    }

    private boolean validateV1PayloadSignature(String flag, String username, String signature) throws JSONException, PrivilagePayloadException {
        try {
            byte[] signatureBytes = SigningUtil.getBytesFromString(signature);
            String expectedUnsignedValue = getV1SignatureInput(flag, username);
            return SigningUtil.verifyMessageAndBytes(authorityPublicKeyString, expectedUnsignedValue, signatureBytes) != null;
        } catch (Exception e) {
            throw new InvalidPrivilegeSignatureException(e);
        }
    }

    private String getV1SignatureInput(String flag, String username) {
        try {
            JSONObject usernameObject = new JSONObject();
            usernameObject.put("username", username);
            JSONObject flagObject = new JSONObject();
            flagObject.put("flag", flag);

            JSONArray array = new JSONArray();
            array.put(usernameObject);
            array.put(flagObject);
            return array.toString();
        } catch (JSONException e) {
            return "";
        }
    }




    protected String[] processV1Payload(JSONObject obj) throws JSONException{
        String username = obj.getString("username");
        String flag = obj.getString("flag");
        String signature = obj.getString("signature");
        return new String[] {flag, username, signature};
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

    public static class MalformedPayloadException extends PrivilagePayloadException{
        public MalformedPayloadException(Exception e) {
            super(e);
        }

    }

    public static class InvalidPrivilegeSignatureException extends PrivilagePayloadException{
        public InvalidPrivilegeSignatureException(String msg) {
            super(msg);
        }

        public InvalidPrivilegeSignatureException(Exception e) {
            super(e);
        }

    }

    public static class UnrecognizedPayloadVersionException extends PrivilagePayloadException{

    }

}
