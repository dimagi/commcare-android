package org.commcare.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.common.api.ApiException;

import org.commcare.connect.ConnectConstants;

import java.util.Locale;

import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectIDManager;
import org.commcare.connect.database.ConnectDatabaseHelper;
import org.commcare.connect.database.ConnectUserDatabaseUtil;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.ConnectNetworkHelper;
import org.commcare.connect.network.IApiCallback;

import android.content.Context;
import android.widget.Toast;

import java.io.InputStream;

import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

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
            // Ignore
        }
        return -1;
    }

    /**
     * Retrieves the country code for the user's current locale.
     */
    public int getCountryCodeFromLocale(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        return phoneNumberUtil.getCountryCodeForRegion(locale.getCountry());
    }

    public String setDefaultCountryCode(Context context) {
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
                        e.printStackTrace();
                    }
                });
    }

    /**
     * Handles the result of a phone number picker request.
     */
    public static String handlePhoneNumberPickerResult(int requestCode, int resultCode, Intent intent, Activity activity) {
        if (requestCode == ConnectConstants.CREDENTIAL_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            SignInClient signInClient = Identity.getSignInClient(activity);
            try {
                return signInClient.getPhoneNumberFromIntent(intent);
            } catch (ApiException ignored) {
            }
        }
        return "";
    }

    public String formatCountryCode(int code) {
        if (code > 0) {
            String codeText = String.valueOf(code);
            return codeText.startsWith("+") ? codeText : "+" + codeText;
        }
        return "";
    }

    public String removeCountryCode(String fullNumber, String codeText) {
        return fullNumber.startsWith(codeText) ? fullNumber.substring(codeText.length()) : fullNumber;
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

    public void storeAlternatePhone(Context context, ConnectUserRecord user, String phone) {
        user.setAlternatePhone(phone);
        ConnectUserDatabaseUtil.storeUser(context, user);
        ConnectDatabaseHelper.setRegistrationPhase(context, ConnectConstants.CONNECT_REGISTRATION_CONFIRM_PIN);
    }

    public void storePrimaryPhone(Context context, ConnectUserRecord user, String phone) {
        user.setPrimaryPhone(phone);
        ConnectUserDatabaseUtil.storeUser(context, user);
    }
}

