package org.commcare.android.tests.processing;

import org.commcare.android.CommCareTestRunner;
import org.commcare.android.util.TestUtils;
import org.commcare.dalvik.BuildConfig;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.util.externalizable.ExtWrapTagged;
import org.javarosa.core.util.test.ExternalizableTest;
import org.javarosa.xform.util.XFormUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * Tests for the serializaiton and deserialzation of XForms.
 * 
 * @author ctsims
 */
@Config(application=org.commcare.dalvik.application.CommCareApplication.class,
        constants = BuildConfig.class)
@RunWith(CommCareTestRunner.class)
public class FormStorageTest {
    @Before
    public void setupTests() {
        TestUtils.initializeStaticTestStorage();
    }
    
    @Test
    public void testRegressionXFormSerializations() {
        FormDef def = XFormUtils.getFormFromResource("/forms/placeholder.xml");
        ExternalizableTest.testExternalizable(new ExtWrapTagged(def), new ExtWrapTagged(), TestUtils.factory, "FormDef");
        //ExtUtil.deserialize(ExtUtil.serialize(def), FormDef.class, TestUtils.factory);
    }
}
