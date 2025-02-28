package org.commcare.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.common.api.ApiException;

import org.commcare.connect.ConnectConstants;

import java.util.Locale;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

/**
 * Utility class for handling phone number-related operations.
 * Provides functions for phone number validation, formatting,
 * country code retrieval, and requesting phone number hints.
 */
public class PhoneNumberHelper {
    private final PhoneNumberUtil phoneNumberUtil;

    // Constructor initializes PhoneNumberUtil
    public PhoneNumberHelper(Context context) {
        this.phoneNumberUtil = PhoneNumberUtil.createInstance(context);
    }

    /**
     * Combines the country code and phone number into a single formatted string.
     * Removes any spaces, dashes, or parentheses from the phone number.
     *
     * @param countryCode The country code as a string (e.g., "+1").
     * @param phone       The phone number as a string.
     * @return A formatted phone number string with no special characters.
     */
    public String buildPhoneNumber(String countryCode, String phone) {
        return String.format("%s%s", countryCode, phone).replaceAll("[-() ]", "");
    }

    /**
     * Validates whether the given phone number is valid.
     */
    public boolean isValidPhoneNumber(String phone) {
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phone, null);
            return phoneNumberUtil.isValidNumber(phoneNumber);
        } catch (NumberParseException e) {
            return false;
        }
    }

    /**
     * Extracts the country code from a given phone number.
     */
    public int getCountryCode(String phone) {
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phone, null);
            return phoneNumberUtil.isValidNumber(phoneNumber) ? phoneNumber.getCountryCode() : -1;
        } catch (NumberParseException e) {
            Log.d("PhoneNumberHelper", "Failed to parse number: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Retrieves the country code for the user's current locale.
     */
    public int getCountryCode(Locale locale) {
        return phoneNumberUtil.getCountryCodeForRegion(locale.getCountry());
    }

    /**
     * Requests a phone number hint from Google Identity API.
     */
    public void requestPhoneNumberHint(ActivityResultLauncher<IntentSenderRequest> phoneNumberHintLauncher, Activity activity) {
        GetPhoneNumberHintIntentRequest hintRequest = GetPhoneNumberHintIntentRequest.builder().build();
        Identity.getSignInClient(activity).getPhoneNumberHintIntent(hintRequest)
                .addOnSuccessListener(pendingIntent -> {
                    try {
                        IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(pendingIntent).build();
                        phoneNumberHintLauncher.launch(intentSenderRequest);
                    } catch (SecurityException | IllegalStateException e) {
                        Log.e("PhoneNumberHelper", "Error launching phone number hint", e);
                    }
                });
    }

    /**
     * Handles the result of a phone number picker request.
     */
    public String handlePhoneNumberPickerResult(int requestCode, int resultCode, Intent intent, Activity activity) {
        if (requestCode == ConnectConstants.CREDENTIAL_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            SignInClient signInClient = Identity.getSignInClient(activity);
            try {
                return signInClient.getPhoneNumberFromIntent(intent);
            } catch (ApiException e) {
                Log.e("PhoneNumberHelper", "Failed to get phone number: " + e.getMessage(), e);
            }
        }
        return "";
    }

    /**
     * Returns the default country code formatted as a string with a "+" prefix.
     */
    public String getDefaultCountryCode() {
        Locale locale = Locale.getDefault(); // Get default locale
        int code = phoneNumberUtil.getCountryCodeForRegion(locale.getCountry());
        return (code > 0) ? "+" + code : "";
    }
}
