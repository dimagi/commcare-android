package org.commcare.android.tests.processing;

import org.commcare.android.shadows.SQLiteDatabaseNative;
import org.commcare.android.util.TestUtils;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.xform.util.XFormUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

/**
 * Tests for the serializaiton and deserialzation of XForms.
 * 
 * @author ctsims
 */
@Config(shadows={SQLiteDatabaseNative.class}, application=org.commcare.dalvik.application.CommCareApplication.class)
@RunWith(RobolectricGradleTestRunner.class)
public class FormStorageTest {
    @Before
    public void setupTests() {
        TestUtils.initializeStaticTestStorage();
    }
    
    @Test
    public void testRegressionXFormSerializations() {
        FormDef def = XFormUtils.getFormFromResource("/resources/forms/placeholder.xml");
        try {
            ExtUtil.deserialize(ExtUtil.serialize(def), FormDef.class, TestUtils.factory);
        } catch (IOException | DeserializationException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        } 
    }
}
