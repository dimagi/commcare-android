package org.commcare.android.tests.formsave;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.util.Pair;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.reference.ReferenceManager;
import org.javarosa.core.reference.ResourceReferenceFactory;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.odk.collect.android.tasks.FormLoaderTask;
import org.odk.collect.android.utilities.ApkUtils;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowEnvironment;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
@Config(application = CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class FormLoadTest {

    @Before
    public void setup() {
        // needed to resolve "jr://resource" type references
        ReferenceManager._().addReferenceFactory(new ResourceReferenceFactory());

        TestUtils.initializeStaticTestStorage();
        TestAppInstaller.setupPrototypeFactory();

        TestAppInstaller appTestInstaller =
                new TestAppInstaller("jr://resource/commcare-apps/form_save_regressions/profile.ccpr",
                        "test", "123");
        appTestInstaller.installAppAndLogin();
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);
    }

    /**
     * Regression test for 2.25.2 hotfix. Issue  where loading form from file resulted in a FormDef with types but loading from serialized object resulted the form record processor
     */
    @Test
    public void testEqualityAfterFormSerialization() throws IOException {
        final Context context = CommCareApplication._().getApplicationContext();
        AndroidSessionWrapper state = CommCareApplication._().getCurrentSessionWrapper();
        CommCareSession session = state.getSession();
        session.setCommand("m0-f0");
        state.commitStub();

        FormRecord record = state.getFormRecord();
        AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
        Uri formUri = platform.getFormContentUri(record.getFormNamespace());
        Pair<String, String> formAndMediaPaths = FormLoaderTask.getFormAndMediaPaths(context, formUri);
        String formPath = formAndMediaPaths.first;
        FormDef formDef = FormLoaderTask.loadFormFromFile(new File(formPath));
        TreeElement rootFromFileLoad = formDef.getMainInstance().getRoot();
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(byteOutputStream);
        byte[] formAsBytes = null;
        try {
            formDef.writeExternal(dos);
            formAsBytes = byteOutputStream.toByteArray();
        } finally {
            dos.close();
        }

        FormDef formDefFromDeserializing = new FormDef();
        DataInputStream inputStream = new DataInputStream(new ByteArrayInputStream(formAsBytes));
        try {
            formDefFromDeserializing.readExternal(inputStream, ApkUtils.getPrototypeFactory(context));
        } catch (DeserializationException e) {
            Assert.fail("failed to deserialize form");
        } finally {
            inputStream.close();
        }

        TreeElement rootFromDeserialization = formDefFromDeserializing.getMainInstance().getRoot();
        Assert.assertEquals(rootFromFileLoad, rootFromDeserialization);
    }
}
