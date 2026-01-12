package org.commcare.utils;

import android.app.Activity;
import android.content.Context;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;

import java.util.Locale;

import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import org.javarosa.core.services.Logger;

/**
 * Helper class for functionality related to phone numbers
 * Includes frequent usage of PhoneNumberUtil
 */
public class PhoneNumberHelper {
    private static PhoneNumberHelper instance;
    private final PhoneNumberUtil phoneNumberUtil;

    // Private constructor to prevent direct instantiation
    private PhoneNumberHelper(Context context) {
        phoneNumberUtil = PhoneNumberUtil.createInstance(context);
    }

    public static synchronized PhoneNumberHelper getInstance(Context context) {
        if (instance == null) {
            instance = new PhoneNumberHelper(context);
        }
        return instance;
    }

    /**
     * Combines the country code and phone number into a single formatted string.
     * Removes any spaces, dashes, or parentheses from the phone number.
     *
     * @param countryCode The country code as a string (e.g., "+1").
     * @param phone       The phone number as a string.
     * @return A formatted phone number string with no special characters.
     */
    public static String buildPhoneNumber(String countryCode, String phone) {
        return String.format("%s%s", countryCode, phone)
                .replaceAll("-", "")
                .replaceAll("\\(", "")
                .replaceAll("\\)", "")
                .replaceAll(" ", "");
    }

    /**
     * Validates whether the given phone number is valid.
     */
    public boolean isValidPhoneNumber(String phone) {
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phone, null);
            return phoneNumberUtil.isValidNumber(phoneNumber);
        } catch (NumberParseException e) {
            Logger.exception("Exception occurred while verifying phone number", e);
            return false;
        }
    }

    /**
     * Extracts the country code from a given phone number.
     */
    public int getCountryCode(String phone) {
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phone, null);
            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
                return phoneNumber.getCountryCode();
            }
        } catch (NumberParseException e) {
            Logger.exception("Exception occurred while getting country code", e);
        }
        return -1;
    }

    /**
     * Extracts the national phone number from a given full phone number.
     */
    public long getNationalNumber(String phone) {
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phone, null);
            if (phoneNumberUtil.isValidNumber(phoneNumber)) {
                return phoneNumber.getNationalNumber();
            }
        } catch (NumberParseException e) {
            Logger.exception("Exception occurred while getting national number", e);
        }
        return -1;
    }

    private int getCountryCodeFromLocale(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        return phoneNumberUtil.getCountryCodeForRegion(locale.getCountry());
    }

    /**
     * Retrieves the country code for the user's current locale.
     */
    public String getDefaultCountryCode(Context context) {
        int code = getCountryCodeFromLocale(context);
        if (code > 0) {
            return "+" + code;
        }
        return "";
    }

    /**
     * Requests a phone number hint from Google Identity API.
     */
    public static void requestPhoneNumberHint(ActivityResultLauncher<IntentSenderRequest> phoneNumberHintLauncher, Activity activity) {
        GetPhoneNumberHintIntentRequest hintRequest = GetPhoneNumberHintIntentRequest.builder().build();
        Identity.getSignInClient(activity).getPhoneNumberHintIntent(hintRequest)
                .addOnSuccessListener(pendingIntent -> {
                    try {
                        IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(pendingIntent).build();
                        phoneNumberHintLauncher.launch(intentSenderRequest);
                    } catch (Exception e) {
                        Logger.exception("Exception occurred while showing google phone number picker", e);
                        e.printStackTrace();
                    }
                }
                ).addOnFailureListener(e -> Logger.exception("Exception occurred while showing google phone number picker", e)
                );
    }

    public String formatCountryCode(int code) {
        if (code > 0) {
            String codeText = String.valueOf(code);
            return codeText.startsWith("+") ? codeText : "+" + codeText;
        }
        return "";
    }

    public TextWatcher getCountryCodeWatcher(EditText editText) {
        return new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().startsWith("+")) {
                    editText.setText("+" + s);
                    editText.setSelection(editText.getText().length());
                }
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
            }
        };
    }
}

