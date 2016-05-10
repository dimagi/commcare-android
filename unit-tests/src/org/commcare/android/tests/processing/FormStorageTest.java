package org.commcare.android.tests.processing;

import org.commcare.CommCareTestApplication;
import org.commcare.android.CommCareTestRunner;
import org.commcare.android.resource.installers.XFormAndroidInstaller;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.xform.util.XFormUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import java.io.IOException;

/**
 * Tests for the serializaiton and deserialzation of XForms.
 *
 * @author ctsims
 */
@Config(application = CommCareTestApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class FormStorageTest {

    @Before
    public void setup() {
        XFormAndroidInstaller.registerAndroidLevelFormParsers();
    }

    @Test
    public void testRegressionXFormSerializations() {
        FormDef def = XFormUtils.getFormFromResource("/forms/placeholder.xml");
        try {
            ExtUtil.deserialize(ExtUtil.serialize(def), FormDef.class,
                    TestUtils.getStaticPrototypeFactory());
        } catch (IOException | DeserializationException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Ensure that a form that has intent callouts can be serialized and deserialized
     */
    @Test
    public void testCalloutSerializations() {
        FormDef def =
                XFormUtils.getFormFromResource("/forms/intent_callout_serialization_test.xml");
        try {
            ExtUtil.deserialize(ExtUtil.serialize(def), FormDef.class, TestUtils.getStaticPrototypeFactory());
        } catch (IOException | DeserializationException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }
}
