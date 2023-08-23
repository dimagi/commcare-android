package org.commcare.utils;

import android.content.Context;

import java.util.Locale;

import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

/**
 * @author dviggiano
 * Helper class for functionality related to phone numbers
 * Includes frequent usage of PhoneNumberUtil
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
}
