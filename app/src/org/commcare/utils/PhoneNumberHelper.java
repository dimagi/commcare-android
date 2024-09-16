package org.commcare.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.SignInClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnSuccessListener;

import org.commcare.android.database.connect.models.ConnectUserRecord;
import org.commcare.connect.ConnectConstants;
import org.commcare.connect.ConnectManager;
import org.commcare.connect.network.ApiConnectId;
import org.commcare.connect.network.IApiCallback;
import org.commcare.dalvik.R;
import org.javarosa.core.services.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

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
    private static PhoneNumberUtil utilStatic = null;

    //Private constructor, class should be used statically
    private PhoneNumberHelper() {
    }

    private static PhoneNumberUtil getUtil(Context context) {
        if (utilStatic == null) {
            utilStatic = PhoneNumberUtil.createInstance(context);
        }

        return utilStatic;
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
            //Error parsing number means it isn't valid, fall-through to return false
        }

        return -1;
    }

    public static int getCountryCode(Context context) {
        Locale locale = context.getResources().getConfiguration().locale;
        PhoneNumberUtil util = getUtil(context);

        return util.getCountryCodeForRegion(locale.getCountry());
    }

    public static void requestPhoneNumberHint(Activity activity) {
        GetPhoneNumberHintIntentRequest hintRequest = GetPhoneNumberHintIntentRequest.builder().build();
        Identity.getSignInClient(activity).getPhoneNumberHintIntent(hintRequest)
                .addOnSuccessListener(new OnSuccessListener<PendingIntent>() {
                    @Override
                    public void onSuccess(PendingIntent pendingIntent) {
                        try {
                            activity.startIntentSenderForResult(pendingIntent.getIntentSender(), ConnectConstants.CREDENTIAL_PICKER_REQUEST, null, 0, 0, 0);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    public static String handlePhoneNumberPickerResult(int requestCode, int resultCode, Intent intent, Activity activity){

        if (requestCode == ConnectConstants.CREDENTIAL_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            SignInClient signInClient = Identity.getSignInClient(activity);
            String phoneNumber;
            try {
                phoneNumber = signInClient.getPhoneNumberFromIntent(intent);
                return phoneNumber;
            } catch (ApiException ignored) {
            }

        }
        return "";
    }
}