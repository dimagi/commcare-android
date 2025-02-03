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
 * Helper class for functionality related to phone numbers
 * Includes frequent usage of PhoneNumberUtil
 *
 * @author dviggiano
 */
public class PhoneNumberHelper {
    private static final ThreadLocal<PhoneNumberUtil> utilStatic = new ThreadLocal<>();
    private static ActivityResultLauncher<IntentSenderRequest> phoneNumberHintLauncher;

    //Private constructor, class should be used statically
    private PhoneNumberHelper() {
    }

    public static void setPhoneNumberHintLauncher(ActivityResultLauncher<IntentSenderRequest> launcher) {
        phoneNumberHintLauncher = launcher;
    }

    private static PhoneNumberUtil getUtil(Context context) {
        PhoneNumberUtil util = utilStatic.get();
        if (util == null) {
            util = PhoneNumberUtil.createInstance(context);
            utilStatic.set(util);
        }

        return util;
    }

    public static String buildPhoneNumber(String countryCode, String phone) {
        return String.format("%s%s", countryCode, phone)
                .replaceAll("-", "")
                .replaceAll("\\(", "")
                .replaceAll("\\)", "")
                .replaceAll(" ", "");
    }

    public static boolean isValidPhoneNumber(Context context, String phone) {
        PhoneNumberUtil util = getUtil(context);
        try {
            Phonenumber.PhoneNumber phoneNumber = util.parse(phone, null);
            return util.isValidNumber(phoneNumber);
        } catch (NumberParseException e) {
            //Error parsing number means it isn't valid, fall-through to return false
        }

        return false;
    }

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

    public static int getCountryCode(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        PhoneNumberUtil util = getUtil(context);

        return util.getCountryCodeForRegion(locale.getCountry());
    }

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

    public static String handlePhoneNumberPickerResult(int requestCode, int resultCode, Intent intent, Activity activity) {

        if (requestCode == ConnectConstants.CREDENTIAL_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            SignInClient signInClient = Identity.getSignInClient(activity);
            String phoneNumber;
            try {
                phoneNumber = signInClient.getPhoneNumberFromIntent(intent);
                return phoneNumber;
            } catch (ApiException e) {
                Log.e("PhoneNumberHelper", "Failed to get phone number: " + e.getMessage(), e);
                return null;
            }

        }
        return "";
    }
}