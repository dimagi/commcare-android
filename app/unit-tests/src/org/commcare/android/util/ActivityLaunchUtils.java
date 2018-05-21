package org.commcare.android.util;

import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.StandardHomeActivity;
import org.commcare.android.mocks.FormAndDataSyncerFake;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionNavigator;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;

import javax.annotation.Nullable;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ActivityLaunchUtils {
    public static ShadowActivity buildHomeActivityForFormEntryLaunch(String sessionCommand) {
        AndroidSessionWrapper sessionWrapper =
                CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand(sessionCommand);
        return buildHomeActivity();
    }

    public static ShadowActivity buildHomeActivity() {
        return buildHomeActivity(true, null);
    }

    public static ShadowActivity buildHomeActivity(boolean startSession, @Nullable Intent intent) {
        StandardHomeActivity homeActivity =
                Robolectric.buildActivity(StandardHomeActivity.class, intent).create().get();
        // make sure we don't actually submit forms by using a fake form submitter
        homeActivity.setFormAndDataSyncer(new FormAndDataSyncerFake());

        if (startSession) {
            SessionNavigator sessionNavigator = homeActivity.getSessionNavigator();
            sessionNavigator.startNextSessionStep();
        }

        return Shadows.shadowOf(homeActivity);
    }
}
