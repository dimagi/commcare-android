package org.commcare.android.util;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.widget.ListView;

import org.commcare.CommCareApplication;
import org.commcare.activities.CommCareHomeActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.FormRecordListActivity;
import org.commcare.adapters.IncompleteFormListAdapter;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.mocks.FormAndDataSyncerFake;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionNavigator;
import org.commcare.suite.model.SessionDatum;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;
import org.robolectric.shadows.ShadowListView;

import java.util.Date;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.fail;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class ActivityLaunchUtils {
    private static final String TAG = ActivityLaunchUtils.class.getSimpleName();

    public static ShadowActivity buildHomeActivityForFormEntryLaunch(String sessionCommand) {
        return buildHomeActivityForFormEntryLaunch(sessionCommand, null);
    }

    public static ShadowActivity buildHomeActivityForFormEntryLaunch(String sessionCommand,
                                                                     String caseId) {
        AndroidSessionWrapper sessionWrapper =
                CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand(sessionCommand);
        if (caseId != null) {
            SessionDatum datum = session.getNeededDatum();
            session.setDatum(datum.getDataId(), caseId);
        }
        return buildHomeActivity();
    }

    public static ShadowActivity buildHomeActivity() {
        CommCareHomeActivity homeActivity =
                Robolectric.buildActivity(CommCareHomeActivity.class).create().get();
        // make sure we don't actually submit forms by using a fake form submitter
        homeActivity.setFormAndDataSyncer(new FormAndDataSyncerFake());
        SessionNavigator sessionNavigator = homeActivity.getSessionNavigator();
        sessionNavigator.startNextSessionStep();
        return Shadows.shadowOf(homeActivity);
    }

    public static void waitForActivityFinish(ShadowActivity shadowActivity) {
        long waitStartTime = new Date().getTime();
        while (!shadowActivity.isFinishing()) {
            Log.d(TAG, "Waiting for the form to save and the form entry activity to finish");
            if ((new Date().getTime()) - waitStartTime > 5000) {
                fail("form entry activity took too long to finish");
            }
        }
    }

    public static FormEntryActivity openAnIncompleteForm(int expectedFormCount, int formIndexToSelect) {
        return openSavedFormInner(expectedFormCount, formIndexToSelect, true);
    }

    public static FormEntryActivity openASavedForm(int expectedFormCount, int formIndexToSelect) {
        return openSavedFormInner(expectedFormCount, formIndexToSelect, false);
    }

    private static FormEntryActivity openSavedFormInner(int expectedFormCount,
                                                        int formIndexToSelect,
                                                        boolean isIncomplete) {
        Intent savedFormsIntent =
                new Intent(RuntimeEnvironment.application, FormRecordListActivity.class);
        if (isIncomplete) {
            savedFormsIntent.putExtra(FormRecord.META_STATUS, FormRecord.STATUS_INCOMPLETE);
        }
        ShadowActivity homeActivityShadow = prepSavedFormsActivity(savedFormsIntent);

        FormRecordListActivity savedFormsActivity =
                Robolectric.buildActivity(FormRecordListActivity.class)
                        .withIntent(savedFormsIntent).create().start()
                        .resume().get();

        // wait for saved forms to load
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        ShadowListView shadowEntityList = assertSavedFormEntries(expectedFormCount, savedFormsActivity, isIncomplete);
        shadowEntityList.performItemClick(formIndexToSelect);

        return launchFormEntryForSavedForm(homeActivityShadow, savedFormsIntent, savedFormsActivity);
    }

    private static ShadowActivity prepSavedFormsActivity(Intent savedFormsIntent) {
        CommCareHomeActivity homeActivity =
                Robolectric.buildActivity(CommCareHomeActivity.class).create().get();
        ShadowActivity homeActivityShadow = Shadows.shadowOf(homeActivity);
        homeActivityShadow.startActivityForResult(savedFormsIntent,
                CommCareHomeActivity.GET_INCOMPLETE_FORM);

        // Call this to remove activity from stack, so we can access future activities...
        homeActivityShadow.getNextStartedActivityForResult();

        return homeActivityShadow;
    }

    private static ShadowListView assertSavedFormEntries(int expectedFormCount,
                                                         FormRecordListActivity savedFormActivity,
                                                         boolean isIncomplete) {
        ListView entityList =
                (ListView)savedFormActivity.findViewById(R.id.screen_entity_select_list);
        IncompleteFormListAdapter adapter =
                (IncompleteFormListAdapter)entityList.getAdapter();
        if (isIncomplete) {
            adapter.setFormFilter(FormRecordListActivity.FormRecordFilter.Incomplete);
        } else {
            adapter.setFormFilter(FormRecordListActivity.FormRecordFilter.Submitted);
        }
        adapter.resetRecords();
        assertEquals(expectedFormCount, adapter.getCount());
        return Shadows.shadowOf(entityList);
    }

    private static FormEntryActivity launchFormEntryForSavedForm(ShadowActivity homeActivityShadow,
                                                                 Intent savedFormsIntent,
                                                                 FormRecordListActivity savedFormsActivity) {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);

        ShadowActivity formRecordShadow = Shadows.shadowOf(savedFormsActivity);
        homeActivityShadow.receiveResult(savedFormsIntent,
                formRecordShadow.getResultCode(),
                formRecordShadow.getResultIntent());
        ShadowActivity.IntentForResult formEntryIntent =
                homeActivityShadow.getNextStartedActivityForResult();
        FormEntryActivity formEntryActivity =
                Robolectric.buildActivity(FormEntryActivity.class)
                        .withIntent(formEntryIntent.intent)
                        .create().start().resume().get();

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        assertNotNull(FormEntryActivity.mFormController);

        return formEntryActivity;
    }
}
