package org.commcare.commcaresupportlibrary;

import android.util.Base64;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class BiometricUtils {

    public enum BiometricIdentifier {
        RIGHT_THUMB, RIGHT_INDEX_FINGER, RIGHT_MIDDLE_FINGER, RIGHT_RING_FINGER, RIGHT_PINKY_FINGER,
        LEFT_THUMB, LEFT_INDEX_FINGER, LEFT_MIDDLE_FINGER, LEFT_RING_FINGER, LEFT_PINKY_FINGER,
        FACE
    }

    /**
     * This method converts the biometrics templates into a Base64 encoded String to send back to
     * CommCare, as part of the response of the callout
     * @param templates
     * @return Base64 encoded string
     */
    public static String convertTemplatesToBase64String(Map<BiometricUtils.BiometricIdentifier, byte[]> templates){
        // In order to reduce the size of the templates, we are converting each byte array into a
        // Base64 encoded String
        Map<Integer, String> templatesBase64Encoded = new HashMap<>(templates.size());
        for (Map.Entry<BiometricUtils.BiometricIdentifier, byte[]> template : templates.entrySet()) {
            templatesBase64Encoded.put(template.getKey().ordinal(),
                    Base64.encodeToString(template.getValue(), Base64.DEFAULT));
        }
        String templatesInJson = new Gson().toJson(templatesBase64Encoded);
        return Base64.encodeToString(templatesInJson.getBytes(), Base64.DEFAULT);
    }
}
