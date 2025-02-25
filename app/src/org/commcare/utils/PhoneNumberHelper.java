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
 * <p>
 * This class leverages the PhoneNumberUtil library for number parsing
 * and validation, and Google Identity API for phone number hint retrieval.
 *
 * @author dviggiano
 */
public class PhoneNumberHelper {
    private static final ThreadLocal<PhoneNumberUtil> utilStatic = new ThreadLocal<>();
    private static ActivityResultLauncher<IntentSenderRequest> phoneNumberHintLauncher;

    // Private constructor to prevent instantiation
    private PhoneNumberHelper() {
    }

    /**
     * Sets the ActivityResultLauncher for handling phone number hint requests.
     *
     * @param launcher The launcher that will handle the result of the phone number hint request.
     */
    public static void setPhoneNumberHintLauncher(ActivityResultLauncher<IntentSenderRequest> launcher) {
        phoneNumberHintLauncher = launcher;
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
     * Validates whether the given phone number is valid based on the region.
     *
     * @param context The application context used for retrieving the PhoneNumberUtil instance.
     * @param phone   The phone number to validate.
     * @return True if the phone number is valid, false otherwise.
     */
    public static boolean isValidPhoneNumber(Context context, String phone) {
        PhoneNumberUtil util = getUtil(context);
        try {
            Phonenumber.PhoneNumber phoneNumber = util.parse(phone, null);
            return util.isValidNumber(phoneNumber);
        } catch (NumberParseException e) {
            // Error parsing number means it isn't valid
        }
        return false;
    }

    /**
     * Extracts the country code from a given phone number.
     *
     * @param context The application context.
     * @param phone   The phone number from which to extract the country code.
     * @return The country code as an integer, or -1 if extraction fails.
     */
    public static int getCountryCode(Context context, String phone) {
        PhoneNumberUtil util = getUtil(context);
        try {
            Phonenumber.PhoneNumber phoneNumber = util.parse(phone, null);
            if (util.isValidNumber(phoneNumber)) {
                return phoneNumber.getCountryCode();
            }
        } catch (NumberParseException e) {
            Log.d("PhoneNumberHelper", "Failed to parse number: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Retrieves the country code for the user's current locale.
     *
     * @param context The application context.
     * @return The country code as an integer.
     */
    public static int getCountryCode(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        PhoneNumberUtil util = getUtil(context);
        return util.getCountryCodeForRegion(locale.getCountry());
    }

    /**
     * Returns the default country code formatted as a string with a "+" prefix.
     *
     * @param context The application context.
     * @return The country code string (e.g., "+1"), or an empty string if unavailable.
     */
    public static String setDefaultCountryCode(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        PhoneNumberUtil util = getUtil(context);

        int code = util.getCountryCodeForRegion(locale.getCountry());
        String codeText = "";
        if (code > 0) {
            codeText = String.format(Locale.getDefault(), "%d", code);
            if (!codeText.startsWith("+")) {
                codeText = "+" + codeText;
            }
        }
        return codeText;
    }

    /**
     * Requests a phone number hint from Google Identity API.
     * This allows the user to pick a phone number without manually entering it.
     *
     * @param activity The activity that initiates the request.
     */
    public static void requestPhoneNumberHint(Activity activity) {
        GetPhoneNumberHintIntentRequest hintRequest = GetPhoneNumberHintIntentRequest.builder().build();
        Identity.getSignInClient(activity).getPhoneNumberHintIntent(hintRequest)
                .addOnSuccessListener(pendingIntent -> {
                    try {
                        IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(pendingIntent).build();
                        phoneNumberHintLauncher.launch(intentSenderRequest);
                    } catch (SecurityException e) {
                        Log.e("PhoneNumberHelper", "Security permission denied", e);
                    } catch (IllegalStateException e) {
                        Log.e("PhoneNumberHelper", "Activity was destroyed", e);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    /**
     * Handles the result of a phone number picker request.
     * Extracts the phone number from the returned intent if available.
     *
     * @param requestCode The request code used to identify the intent result.
     * @param resultCode  The result code indicating success or failure.
     * @param intent      The intent containing the selected phone number.
     * @param activity    The activity that initiated the request.
     * @return The selected phone number as a string, or an empty string if unavailable.
     */
    public static String handlePhoneNumberPickerResult(int requestCode, int resultCode, Intent intent, Activity activity) {
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
     * Retrieves a thread-local instance of PhoneNumberUtil to parse phone numbers.
     *
     * @param context The application context.
     * @return The PhoneNumberUtil instance.
     */
    private static PhoneNumberUtil getUtil(Context context) {
        PhoneNumberUtil util = utilStatic.get();
        if (util == null) {
            util = PhoneNumberUtil.createInstance(context);
            utilStatic.set(util);
        }
        return util;
    }
}
