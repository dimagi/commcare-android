package org.commcare.android.util;

import android.content.Intent;

import org.commcare.CommCareApplication;
import org.commcare.activities.EntitySelectActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.StandardHomeActivity;
import org.commcare.android.mocks.FormAndDataSyncerFake;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionNavigator;
import org.junit.Assert;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;

import javax.annotation.Nullable;

import static org.junit.Assert.assertTrue;

/**
 * utility functions to launch common activities on a particular session path
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ActivityLaunchUtils {

    /**
     * Launch form entry assuming the current session state points to a form
     * @return instance to the FormEntryActivity acquired using the current session state
     */
    public static FormEntryActivity launchFormEntry() {
        ShadowActivity shadowActivity = buildHomeActivity();

        Intent formEntryIntent = shadowActivity.getNextStartedActivity();
        // make sure the form entry activity should be launched
        String intentActivityName = formEntryIntent.getComponent().getClassName();
        assertTrue(intentActivityName.equals(FormEntryActivity.class.getName()));

        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class, formEntryIntent)
                        .create().start().resume().get();
        return formEntryActivity;
    }

    public static FormEntryActivity launchFormEntry(String command) {
        addCommandToSession(command);
        return launchFormEntry();
    }

    public static EntitySelectActivity launchEntitySelectActivity(String command) {
        ShadowActivity shadowHomeActivity = buildHomeActivityForFormEntryLaunch(command);

        Intent entitySelectIntent = shadowHomeActivity.getNextStartedActivity();

        String intentActivityName = entitySelectIntent.getComponent().getClassName();
        Assert.assertEquals(EntitySelectActivity.class.getName(), intentActivityName);

        // start the entity select activity
        return Robolectric.buildActivity(EntitySelectActivity.class, entitySelectIntent)
                .setup().get();
    }

    public static void addCommandToSession(String sessionCommand) {
        AndroidSessionWrapper sessionWrapper =
                CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand(sessionCommand);
    }

    public static ShadowActivity buildHomeActivityForFormEntryLaunch(String sessionCommand) {
        addCommandToSession(sessionCommand);
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
