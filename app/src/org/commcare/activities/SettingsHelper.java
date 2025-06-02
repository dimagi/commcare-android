package org.commcare.activities;

import android.content.Context;
import android.content.Intent;

public class SettingsHelper {
    public static void launchDateSettings(Context context) {
        context.startActivity(new Intent(android.provider.Settings.ACTION_DATE_SETTINGS));
    }

    public static void launchSecuritySettings(Context context) {
        context.startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS));
    }
}
