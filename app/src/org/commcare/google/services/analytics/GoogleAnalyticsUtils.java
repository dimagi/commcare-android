package org.commcare.google.services.analytics;

import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceManager;

import com.google.android.gms.analytics.HitBuilders;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareSetupActivity;
import org.commcare.android.logging.ReportingUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.utils.EncryptionUtils;

import java.util.Map;

/**
 * All methods used to report events to google analytics, and all supporting utils
 *
 * @author amstone
 */
public class GoogleAnalyticsUtils {


    /**
     * Report a google analytics event that has a category, action, and label
     */
    private static void reportEvent(String category, String action, String label) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }
        getAnalyticsInstance().send(new HitBuilders.EventBuilder()
                .setCustomDimension(1, CommCareApplication.instance().getCurrentUserId())
                .setCustomDimension(2, ReportingUtils.getDomain())
                .setCustomDimension(3, BuildConfig.FLAVOR)
                .setCustomDimension(4, "" + CommCareApplication.instance().isConsumerApp())
                .setCustomDimension(5, ReportingUtils.getAppId())
                .setCategory(category)
                .setAction(action)
                .setLabel(label)
                .build());
    }

    /**
     * Report a google analytics event that has a category, action, label, and value
     */
    private static void reportEvent(String category, String action, String label, int value) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }
        getAnalyticsInstance().send(new HitBuilders.EventBuilder()
                .setCustomDimension(1, CommCareApplication.instance().getCurrentUserId())
                .setCustomDimension(2, ReportingUtils.getDomain())
                .setCustomDimension(3, BuildConfig.FLAVOR)
                .setCustomDimension(4, "" + CommCareApplication.instance().isConsumerApp())
                .setCustomDimension(5, ReportingUtils.getAppId())
                .setCategory(category)
                .setAction(action)
                .setLabel(label)
                .setValue(value)
                .build());
    }

    public static void reportAdvancedActionItemClick(String action) {
        reportEvent(GoogleAnalyticsFields.CATEGORY_ADVANCED_ACTIONS, action);
    }

    /**
     * Report a user event of navigating backward out of the entity detail screen
     *
     * @param isSwipe - Toggles user's method of navigation to swipe or arrow press
     */
    public static void reportEntityDetailExit(boolean isSwipe, boolean isSingleTab) {
        reportEntityDetailNavigation(
                GoogleAnalyticsFields.ACTION_EXIT_FROM_DETAIL, isSwipe, isSingleTab);
    }

    /**
     * Report a user event of continuing forward out of the entity detail screen
     *
     * @param isSwipe - Toggles user's method of navigation to swipe or arrow press
     */
    public static void reportEntityDetailContinue(boolean isSwipe, boolean isSingleTab) {
        reportEntityDetailNavigation(
                GoogleAnalyticsFields.ACTION_CONTINUE_FROM_DETAIL, isSwipe, isSingleTab);
    }

    private static void reportEntityDetailNavigation(String action, boolean isSwipe, boolean isSingleTab) {
        reportEvent(
                GoogleAnalyticsFields.CATEGORY_MODULE_NAVIGATION,
                action,
                isSwipe ? GoogleAnalyticsFields.LABEL_SWIPE : GoogleAnalyticsFields.LABEL_ARROW,
                isSingleTab ? GoogleAnalyticsFields.VALUE_DOESNT_HAVE_TABS : GoogleAnalyticsFields.VALUE_HAS_TABS);
    }

    /**
     * Report the length of a certain user event/action/concept
     *
     * @param action - Communicates the event/action/concept whose length is being measured
     * @param value  - Communicates the duration, in seconds
     */
    public static void reportTimedEvent(String action, int value) {
        if (analyticsDisabled() || versionIncompatible()) {
            return;
        }
        getAnalyticsInstance().send(new HitBuilders.EventBuilder()
                .setCustomDimension(1, CommCareApplication.instance().getCurrentUserId())
                .setCustomDimension(2, ReportingUtils.getDomain())
                .setCustomDimension(3, BuildConfig.FLAVOR)
                .setCategory(GoogleAnalyticsFields.CATEGORY_TIMED_EVENTS)
                .setAction(action)
                .setValue(value)
                .build());
    }

}
