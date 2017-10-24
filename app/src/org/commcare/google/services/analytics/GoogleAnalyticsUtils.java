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

}
