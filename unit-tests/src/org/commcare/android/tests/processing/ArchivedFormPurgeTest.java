package org.commcare.android.tests.processing;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.SavedFormLoader;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.odk.collect.android.logic.ArchivedFormManagement;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/**
 * Ensure logic that purges old saved forms is correct.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class ArchivedFormPurgeTest {
    private static CommCareApp ccApp;
    private static DateTime startTestDate;
    private static int SAVED_FORM_COUNT = 5;

    @Before
    public void setup() {
        // needed to resolve "jr://resource" type references
        ReferenceManager._().addReferenceFactory(new ResourceReferenceFactory());

        TestAppInstaller.setupPrototypeFactory();

        TestAppInstaller appTestInstaller =
                new TestAppInstaller("jr://resource/commcare-apps/archive_form_tests/profile.ccpr",
                        "test", "123");
        appTestInstaller.installAppAndLogin();

        SavedFormLoader.loadFormsFromPayload("/commcare-apps/archive_form_tests/saved_form_payload.xml");
        ccApp = CommCareApplication._().getCurrentApp();

        String firstFormCompletionDate = "Wed Oct 05 16:17:01 -0400 2015";
        DateTimeFormatter dtf = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss Z yyyy");
        startTestDate = dtf.parseDateTime(firstFormCompletionDate);
    }

    @Test
    public void testSavedFormPurge() {
        DateTime twoMonthsLater = startTestDate.plusMonths(2);
        assertEquals("All but one form should be purged if we are two months past the first forms create date.",
                SAVED_FORM_COUNT - 1, ArchivedFormManagement.getSavedFormsToPurge(twoMonthsLater).size());

        DateTime twentyYearsLater = startTestDate.plusYears(20);
        assertEquals("All forms should be purged if we are way in the future.",
                SAVED_FORM_COUNT, ArchivedFormManagement.getSavedFormsToPurge(twentyYearsLater).size());

        assertEquals("No forms should be purged if the current time matches the first form creation time",
                0, ArchivedFormManagement.getSavedFormsToPurge(startTestDate).size());
    }

    @Test
    public void testPurgeDateLoading() {
        int daysFormValidFor = ArchivedFormManagement.getArchivedFormsValidityInDays(ccApp);
        assertEquals("App should try to keep forms for 31 days", 31, daysFormValidFor);
    }
}
