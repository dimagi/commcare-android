package org.commcare.android.tests.caselist;

import org.commcare.CommCareApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class EntityListCalloutDataTest {
    private final static String TAG = EntityListCalloutDataTest.class.getSimpleName();

    @Before
    public void setup() {
        TestAppInstaller.initInstallAndLogin("jr://resource/commcare-apps/case_list_lookup/profile.ccpr", "test", "123");

        TestUtils.processResourceTransaction("/commcare-apps/case_list_lookup/restore.xml");
    }

    @Test
    public void testAttachCalloutResultToEntityList() {
    }
}
