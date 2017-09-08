package org.commcare.android.nfc;

import android.annotation.TargetApi;
import android.nfc.NdefRecord;
import android.os.Build;

import org.javarosa.core.services.locale.Localization;

import java.nio.charset.Charset;
import java.util.Locale;

/**
 * Created by amstone326 on 9/8/17.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class NdefRecordUtil {

    private static final String CHARSET_ENCODING = "UTF-8";

    protected static String readValueFromRecord(NdefRecord record) {
        return new String(record.getPayload(), Charset.forName(CHARSET_ENCODING));
    }

    protected static NdefRecord createNdefRecord(String userSpecifiedType,
                                                 String userSpecifiedDomain,
                                                 String payloadToWrite) {
        if (NfcActivity.isCommCareSupportedWellKnownType(userSpecifiedType)) {
            return createWellKnownTypeRecord(userSpecifiedType, payloadToWrite);
        } else {
            return createExternalRecord(userSpecifiedType, userSpecifiedDomain, payloadToWrite);
        }
    }

    private static NdefRecord createWellKnownTypeRecord(String type, String payload) {
        if (type.equals("text")) {
            return createTextRecord(payload);
        } else {
            throw new IllegalArgumentException(Localization.get("nfc.well.known.type.not.supported"));
        }
    }

    private static NdefRecord createTextRecord(String payload) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return NdefRecord.createTextRecord(null, payload);
        } else {
            return createTextRecord(payload, Locale.getDefault());
        }
    }

    // Copied from https://developer.android.com/guide/topics/connectivity/nfc/nfc.html#well-known-text
    public static NdefRecord createTextRecord(String payload, Locale locale) {
        byte[] langBytes = locale.getLanguage().getBytes(Charset.forName("US-ASCII"));
        byte[] textBytes = payload.getBytes(Charset.forName(CHARSET_ENCODING));

        int utfBit = 0;
        char status = (char)(utfBit + langBytes.length);
        byte[] data = new byte[1 + langBytes.length + textBytes.length];
        data[0] = (byte)status;

        System.arraycopy(langBytes, 0, data, 1, langBytes.length);
        System.arraycopy(textBytes, 0, data, 1 + langBytes.length, textBytes.length);

        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], data);
    }

    private static NdefRecord createExternalRecord(String type, String domain, String payload) {
        return NdefRecord.createExternal(domain, type, payload.getBytes(Charset.forName(CHARSET_ENCODING)));
    }
}
