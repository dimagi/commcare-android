package org.commcare.heartbeat;

import android.app.Activity;
import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.PromptUpdateActivity;

import java.util.Date;

/**
 * This class is being used in a custom .apk for field testing of the Prompted Update feature.
 * It is for testing only and will not be merged into any live version of CommCare
 *
 * Created by amstone326 on 6/22/17.
 */
public class UpdatePromptFieldTesting {

    private static final String APK_VERSION_FOR_UPDATE = "2.37.0";
    private static final long DAY_IN_MS = 1000 * 60 * 60 * 24;

    public static void promptForUpdate(Activity context) {
        Intent i = new Intent(context, PromptUpdateActivity.class);
        context.startActivity(i);
    }

    public static UpdateToPrompt generateRandomUpdateToPrompt(boolean forApkUpdate, boolean allowNullReturn) {
        if (allowNullReturn) {
            double random = Math.random();
            if (random < .5) {
                return null;
            }
        }

        String version;
        if (forApkUpdate) {
            version = APK_VERSION_FOR_UPDATE;
        } else {
            int currentAppVersion =
                    CommCareApplication.instance().getCommCarePlatform().getCurrentProfile().getVersion();
            // we just want this to be higher than the current app version
            version = ""+(currentAppVersion + 1);
        }

        return new UpdateToPrompt(version, generateForceByDate(), forApkUpdate);
    }

    private static Date generateForceByDate() {
        double random = Math.random();
        if (random < .5) {
            return null;
        }

        Date twoDaysAgo = new Date(System.currentTimeMillis() - (2*DAY_IN_MS));
        return twoDaysAgo;
    }
}
