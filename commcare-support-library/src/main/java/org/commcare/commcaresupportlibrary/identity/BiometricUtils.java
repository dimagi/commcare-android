package org.commcare.commcaresupportlibrary.identity;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class BiometricUtils {

    /**
     * This method converts the biometrics templates into a Base64 encoded String to send back to
     * CommCare, as part of the response of the callout
     *
     * @param templates Map containing biometric templates
     * @return Base64 encoded string
     */
    public static String convertMapTemplatesToBase64String(Map<BiometricIdentifier,
            byte[]> templates) {
        // In order to reduce the size of the templates, we are converting each byte array into a
        // Base64 encoded String
        Map<Integer, String> templatesBase64Encoded = new HashMap<>(templates.size());
        for (Map.Entry<BiometricIdentifier, byte[]> template : templates.entrySet()) {
            templatesBase64Encoded.put(template.getKey().ordinal(),
                    Base64.encodeToString(template.getValue(), Base64.DEFAULT));
        }
        String templatesInJson = new Gson().toJson(templatesBase64Encoded);
        return Base64.encodeToString(templatesInJson.getBytes(), Base64.DEFAULT);
    }

    /**
     * This method converts the Base64 encoded biometric templates to its original form
     *
     * @param base64EncodedTemplates String containing Base64 encoded biometric templates
     * @return Map containing biometric templates
     */
    public static Map<BiometricIdentifier, byte[]> convertBase64StringTemplatesToMap(
            String base64EncodedTemplates) {
        if (base64EncodedTemplates == null || base64EncodedTemplates.isEmpty()) {
            return null;
        }

        Map<Integer, String> templatesBase64Encoded = null;
        try {
            String templatesInJson = new String(Base64.decode(base64EncodedTemplates.getBytes(),
                    Base64.DEFAULT));
            Type mapType = new TypeToken<HashMap<Integer, String>>() {}.getType();
            templatesBase64Encoded = new Gson().fromJson(templatesInJson, mapType);
        } catch (IllegalArgumentException | UnsupportedOperationException | JsonSyntaxException e) {
            return null;
        }

        Map<BiometricIdentifier, byte[]> templates = new HashMap<>(templatesBase64Encoded.size());
        for (Map.Entry<Integer, String> template : templatesBase64Encoded.entrySet()) {
            templates.put(BiometricIdentifier.values()[template.getKey()],
                    Base64.decode(template.getValue(), Base64.DEFAULT));
        }
        return templates;
    }
}
