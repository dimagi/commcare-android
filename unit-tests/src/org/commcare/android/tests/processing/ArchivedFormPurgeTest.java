package org.commcare.android.tests.processing;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.tasks.PurgeStaleArchivedFormsTask;
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
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/**
 * Tests correctness of saved form purging logic.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class ArchivedFormPurgeTest {

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
    }

    /**
     * Ensure that the correct number of forms are purged given different
     * validity ranges
     */
    @Test
    public void testSavedFormPurge() {
        int SAVED_FORM_COUNT = 5;

        String firstFormCompletionDate = "Mon Oct 05 16:17:01 -0400 2015";
        DateTimeFormatter dtf = DateTimeFormat.forPattern("EEE MMM dd HH:mm:ss Z yyyy");
        DateTime startTestDate = dtf.parseDateTime(firstFormCompletionDate);

        DateTime twoMonthsLater = startTestDate.plusMonths(2);
        assertEquals("Only 1 form should remain if we're 2 months past the 1st form's create date.",
                SAVED_FORM_COUNT - 1,
                PurgeStaleArchivedFormsTask.getSavedFormsToPurge(twoMonthsLater).size());

        DateTime twentyYearsLater = startTestDate.plusYears(20);
        assertEquals("All forms should be purged if we are way in the future.",
                SAVED_FORM_COUNT,
                PurgeStaleArchivedFormsTask.getSavedFormsToPurge(twentyYearsLater).size());

        assertEquals("When the time is the 1st form's creation time, no forms should be purged",
                0,
                PurgeStaleArchivedFormsTask.getSavedFormsToPurge(startTestDate).size());
    }

    @Test
    public void testPurgeDateLoading() {
        CommCareApp ccApp = CommCareApplication._().getCurrentApp();
        int daysFormValidFor = PurgeStaleArchivedFormsTask.getArchivedFormsValidityInDays(ccApp);
        assertEquals("App should try to keep forms for 31 days", 31, daysFormValidFor);
    }
}

