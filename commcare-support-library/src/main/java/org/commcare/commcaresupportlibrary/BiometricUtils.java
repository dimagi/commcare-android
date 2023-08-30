package org.commcare.commcaresupportlibrary;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

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

    /**
     * This method converts the Base64 encoded biometric templates to its original form
     * @param base64EncodedTemplates String containing Base64 encoded biometric templates
     * @return Map containing biometric templates
     */
    public static Map<BiometricUtils.BiometricIdentifier, byte[]> convertBase64StringTemplatesToMap(String base64EncodedTemplates){
        if (base64EncodedTemplates == null || base64EncodedTemplates.isEmpty()) {
            return null;
        }

        Map<Integer, String> templatesBase64Encoded = null;
        try {
            String templatesInJson = new String(Base64.decode(base64EncodedTemplates.getBytes(),
                    Base64.DEFAULT));
            templatesBase64Encoded = new Gson().fromJson(templatesInJson,
                    (new TypeToken<HashMap<Integer, String>>() {}.getType()));
        }
        catch(IllegalArgumentException | UnsupportedOperationException | JsonSyntaxException e){
            return null;
        }

        Map<BiometricUtils.BiometricIdentifier, byte[]> templates
                = new HashMap<>(templatesBase64Encoded.size());
        for (Map.Entry<Integer, String> template : templatesBase64Encoded.entrySet()) {
            templates.put(BiometricIdentifier.values()[template.getKey()],
                    Base64.decode(template.getValue(), Base64.DEFAULT));
        }
        return templates;
    }
}
