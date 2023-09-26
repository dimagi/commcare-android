package org.commcare.commcaresupportlibrary.identity;

import android.util.Base64;
import android.util.Pair;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

public class BiometricUtils {

    /**
     * This method converts the biometric template into a Base64 encoded String to send back to
     * CommCare, as part of the callout response
     *
     * @param biometricIdentifier indicates the type of biometric element captured
     * @param template inputs the biometric template
     * @return Base64 encoded string
     */
    public static String convertTemplateToBase64String(BiometricIdentifier biometricIdentifier,
                                                       byte[] template) {
        // In order to reduce the size of the outpput, the byte array is converted to Base64 before
        // the instantiation of the pair object
        Pair<Integer, String> templatePairBase64Encoded = new Pair<>(biometricIdentifier.ordinal(),
                Base64.encodeToString(template, Base64.DEFAULT));
        String templatesInJson = new Gson().toJson(templatePairBase64Encoded);
        return Base64.encodeToString(templatesInJson.getBytes(), Base64.DEFAULT);
    }

    /**
     * This method converts the Base64 encoded biometric template to its original form
     *
     * @param base64EncodedTemplatePair String containing a Base64 encoded biometric template
     * @return Pair containing the biometric template
     */
    public static Pair<BiometricIdentifier, byte[]> convertBase64StringToTemplatePair(
            String base64EncodedTemplatePair) {
        if (base64EncodedTemplatePair == null || base64EncodedTemplatePair.isEmpty()) {
            return null;
        }

        Pair<Integer, String> templatePairBase64Encoded = null;
        try {
            String templatePairInJson = new String(Base64.decode(base64EncodedTemplatePair.getBytes(),
                    Base64.DEFAULT));
            Type mapType = new TypeToken<Pair<Integer, String>>() {
            }.getType();
            templatePairBase64Encoded = new Gson().fromJson(templatePairInJson, mapType);
        } catch (IllegalArgumentException | UnsupportedOperationException | JsonSyntaxException e) {
            return null;
        }

        Pair<BiometricIdentifier, byte[]> template = new Pair<>(
                BiometricIdentifier.values()[templatePairBase64Encoded.first],
                Base64.decode(templatePairBase64Encoded.second, Base64.DEFAULT));
        return template;
    }
}
