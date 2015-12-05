package org.odk.collect.android.logic;

import android.content.Context;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.javarosa.core.services.IPropertyManager;
import org.javarosa.core.services.properties.IPropertyRules;

import java.util.HashMap;
import java.util.Vector;

/**
 * Used to return device properties to JavaRosa
 * 
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */

public class PropertyManager implements IPropertyManager {

    private static final String t = "PropertyManager";

    private final HashMap<String, String> mProperties;

    private final static String DEVICE_ID_PROPERTY = "deviceid"; // imei
    private final static String SUBSCRIBER_ID_PROPERTY = "subscriberid"; // imsi
    private final static String SIM_SERIAL_PROPERTY = "simserial";
    private final static String PHONE_NUMBER_PROPERTY = "phonenumber";


    public String getName() {
        return "Property Manager";
    }


    public PropertyManager(Context context) {
        Log.i(t, "calling constructor");

        mProperties = new HashMap<>();
        TelephonyManager mTelephonyManager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);

        String deviceId = mTelephonyManager.getDeviceId();
        if (deviceId != null && (deviceId.contains("*") || deviceId.contains("000000000000000"))) {
            deviceId =
                Settings.Secure
                        .getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
        mProperties.put(DEVICE_ID_PROPERTY, deviceId);
        mProperties.put(SUBSCRIBER_ID_PROPERTY, mTelephonyManager.getSubscriberId());
        mProperties.put(SIM_SERIAL_PROPERTY, mTelephonyManager.getSimSerialNumber());
        mProperties.put(PHONE_NUMBER_PROPERTY, mTelephonyManager.getLine1Number());
    }

    @Override
    public Vector<String> getProperty(String propertyName) {
        return null;
    }

    @Override
    public String getSingularProperty(String propertyName) {
        return mProperties.get(propertyName.toLowerCase());
    }

    @Override
    public void setProperty(String propertyName, String propertyValue) {
    }


    @Override
    public void setProperty(String propertyName, @SuppressWarnings("rawtypes") Vector propertyValue) {

    }

    @Override
    public void addRules(IPropertyRules rules) {

    }

    @Override
    public Vector<IPropertyRules> getRules() {
        return null;
    }
}
