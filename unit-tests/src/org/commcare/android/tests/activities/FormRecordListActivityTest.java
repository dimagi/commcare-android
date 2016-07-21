package org.commcare.android.tests.activities;

import android.widget.ListView;

import org.commcare.CommCareTestApplication;
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
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

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
        SavedFormLoader.loadFormsFromPayload("/commcare-apps/form_nav_tests/form_instances_restore.xml", FormRecord.STATUS_SAVED);
    }

    /**
     * Opens up the saved form list activity and checks that there are 2 forms listed
     */
    @Test
    public void openSavedFormViewTest() {
        FormRecordListActivity homeActivity =
                Robolectric.buildActivity(FormRecordListActivity.class).create().start().resume().get();
        // wait for entities to load
        Robolectric.flushBackgroundThreadScheduler();
        Robolectric.flushForegroundThreadScheduler();
        ListView entityList =
                (ListView)homeActivity.findViewById(R.id.screen_entity_select_list);
        IncompleteFormListAdapter adapter = (IncompleteFormListAdapter)entityList.getAdapter();
        adapter.setFormFilter(FormRecordListActivity.FormRecordFilter.Submitted);
        adapter.resetRecords();
        assertEquals(2, adapter.getCount());
    }
}
