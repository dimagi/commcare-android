package org.commcare.android.tests.formsave;

import android.content.Context;

import junit.framework.Assert;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.tasks.FormRecordCleanupTask;
import org.commcare.android.util.StorageUtils;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.util.CommCarePlatform;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Vector;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class UnsentFormTests {

    @Before
    public void setup() {
        // needed to resolve "jr://resource" type references
        ReferenceManager._().addReferenceFactory(new ResourceReferenceFactory());

        TestAppInstaller.setupPrototypeFactory();

        TestAppInstaller appTestInstaller =
                new TestAppInstaller("jr://resource/commcare-apps/archive_form_tests/profile.ccpr",
                        "test", "123");
        appTestInstaller.installAppAndLogin();
    }

    @Test
    public void testUnsentFormCount() {
        SqlStorage<FormRecord> storage = CommCareApplication._().getUserStorage(FormRecord.class);
        int unsent = StorageUtils.getUnsentOrUnprocessedFormsForCurrentApp(storage).size();
        Assert.assertEquals(unsent, 0);

        TestUtils.processResourceTransaction("/commcare-apps/archive_form_tests/saved_form_payload.xml");
        CommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
        Context context = CommCareApplication._();
        Vector<Integer> unindexedRecords =
                storage.getIDsForValues(new String[]{FormRecord.META_STATUS}, new String[]{FormRecord.STATUS_UNINDEXED});
        for (int recordID : unindexedRecords) {
            FormRecord r = storage.read(recordID);

            try {
                FormRecordCleanupTask.updateAndWriteUnindexedRecordTo(context, platform, r, storage, FormRecord.STATUS_UNSENT);
            } catch (InvalidStructureException | XmlPullParserException | IOException | UnfullfilledRequirementsException e) {
            }
        }
        unsent = StorageUtils.getUnsentOrUnprocessedFormsForCurrentApp(storage).size();
        Assert.assertEquals(unsent, 5);
    }
}
