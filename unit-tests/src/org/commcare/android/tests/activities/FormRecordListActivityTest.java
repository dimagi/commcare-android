package org.commcare.android.tests.activities;

import android.content.Intent;
import android.os.Environment;
import android.widget.ListView;

import org.commcare.CommCareTestApplication;
import org.commcare.activities.CommCareHomeActivity;
import org.commcare.activities.FormEntryActivity;
import org.commcare.activities.FormRecordListActivity;
import org.commcare.adapters.IncompleteFormListAdapter;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.util.SavedFormLoader;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowEnvironment;
import org.robolectric.shadows.ShadowListView;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class FormRecordListActivityTest {
    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/form_nav_tests/profile.ccpr",
                "test", "123");
        SavedFormLoader.loadFormsFromPayload("/commcare-apps/form_nav_tests/form_instances_restore.xml",
                FormRecord.STATUS_SAVED);
    }

    /**
     * Opens up the saved form list activity, checks that 2 forms are listed,
     * and opens the first one
     */
    @Test
    public void openSavedFormViewTest() {
        openASavedForm(2, 0);
    }

    public static void openASavedForm(int expectedFormCount, int formIndexToSelect) {
        Intent savedFormsIntent =
                new Intent(RuntimeEnvironment.application, FormRecordListActivity.class);
        ShadowActivity homeActivityShadow = prepSavedFormsActivity(savedFormsIntent);

        FormRecordListActivity savedFormsActivity =
                Robolectric.buildActivity(FormRecordListActivity.class)
                        .withIntent(savedFormsIntent).create().start()
                        .resume().get();

        // wait for saved forms to load
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        ShadowListView shadowEntityList = assertSavedFormEntries(expectedFormCount, savedFormsActivity);
        shadowEntityList.performItemClick(formIndexToSelect);

        launchFormEntryForSavedForm(homeActivityShadow, savedFormsIntent, savedFormsActivity);
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
                                                         FormRecordListActivity savedFormActivity) {
        ListView entityList =
                (ListView)savedFormActivity.findViewById(R.id.screen_entity_select_list);
        IncompleteFormListAdapter adapter =
                (IncompleteFormListAdapter)entityList.getAdapter();
        adapter.setFormFilter(FormRecordListActivity.FormRecordFilter.Submitted);
        adapter.resetRecords();
        assertEquals(expectedFormCount, adapter.getCount());
        return Shadows.shadowOf(entityList);
    }

    private static void launchFormEntryForSavedForm(ShadowActivity homeActivityShadow,
                                                    Intent savedFormsIntent,
                                                    FormRecordListActivity savedFormsActivity) {
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);

        ShadowActivity formRecordShadow = Shadows.shadowOf(savedFormsActivity);
        homeActivityShadow.receiveResult(savedFormsIntent,
                formRecordShadow.getResultCode(),
                formRecordShadow.getResultIntent());
        ShadowActivity.IntentForResult formEntryIntent =
                homeActivityShadow.getNextStartedActivityForResult();
        Robolectric.buildActivity(FormEntryActivity.class)
                        .withIntent(formEntryIntent.intent)
                        .create().start().resume().get();

        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();

        assertNotNull(FormEntryActivity.mFormController);
    }
}
