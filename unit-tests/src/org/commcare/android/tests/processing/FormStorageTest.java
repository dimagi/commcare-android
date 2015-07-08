/**
 * 
 */
package org.commcare.android.tests.processing;

import java.io.IOException;

import org.commcare.android.junit.CommCareTestRunner;
import org.commcare.android.shadows.SQLiteDatabaseNative;
import org.commcare.android.util.LivePrototypeFactory;
import org.commcare.android.util.TestUtils;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.xform.util.XFormUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * @author ctsims
 *
 */
@Config(shadows={SQLiteDatabaseNative.class}, emulateSdk = 18, application=org.commcare.dalvik.application.CommCareApplication.class)
@RunWith(CommCareTestRunner.class)
public class FormStorageTest {
    @Before
    public void setupTests() {
        TestUtils.initializeStaticTestStorage();
    }
    
    @Test
    public void testKeyRecordParse() {
        FormDef def = XFormUtils.getFormFromResource("/resources/forms/placeholder.xml");
        try {
            ExtUtil.deserialize(ExtUtil.serialize(def), FormDef.class, TestUtils.factory);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        } catch (DeserializationException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }
}
