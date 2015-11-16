package org.commcare.android.analytics;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import org.commcare.dalvik.application.CommCareApplication;

/**
 * Created by amstone326 on 11/13/15.
 */
public class GoogleAnalyticsUtils {

    public static void reportAction(String category, String action) {
        getTracker().send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .build());
    }

    public static void reportAction(String category, String action, String label) {
        getTracker().send(new HitBuilders.EventBuilder()
                .setCategory(category)
                .setAction(action)
                .setLabel(label)
                .build());
    }

    public static void reportFormQuitAttempt(String label) {
        reportAction(GoogleAnalyticsFields.CATEGORY_FORM_ENTRY,
                GoogleAnalyticsFields.ACTION_QUIT_ATTEMPT, label);
    }

    public static void reportButtonClick(String screenName, String buttonLabel) {
        getTracker(screenName).send(new HitBuilders.EventBuilder()
                .setCategory(GoogleAnalyticsFields.CATEGORY_HOME_SCREEN)
                .setAction(GoogleAnalyticsFields.ACTION_BUTTON)
                .setLabel(buttonLabel)
                .build());
    }

    public static void reportMenuEntry(String category) {
        reportAction(category, GoogleAnalyticsFields.ACTION_OPTIONS_MENU);
    }

    public static void reportMenuItemEntry(String category, String label) {
        reportAction(category, GoogleAnalyticsFields.ACTION_MENU_ITEM, label);
    }

    public static void reportEnterEditSetting(String category, String label) {
        reportEnterEditSetting(category, label, -1);
    }

    public static void reportEnterEditSetting(String category, String label, int value) {
        HitBuilders.EventBuilder builder = new HitBuilders.EventBuilder();
        builder.setCategory(category).
                setAction(GoogleAnalyticsFields.ACTION_EDIT_SETTING).
                setLabel(label);
        if (value != -1) {
            builder.setValue(value);
        }
        getTracker().send(builder.build());
    }

    private static Tracker getTracker(String screenName) {
        Tracker t = CommCareApplication._().getDefaultTracker();
        t.setScreenName(screenName);
        return t;
    }

    private static Tracker getTracker() {
        return CommCareApplication._().getDefaultTracker();
    }

}
