/**
 * 
 */
package org.commcare.android.tests.processing;

import static junit.framework.Assert.assertEquals;

import org.commcare.android.database.user.models.ACase;
import org.commcare.android.junit.CommCareTestRunner;
import org.commcare.android.shadows.SQLiteDatabaseNative;
import org.commcare.android.util.LivePrototypeFactory;
import org.commcare.android.util.TestUtils;
import org.commcare.cases.model.Case;
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
public class KeyRecordTest {

    //TODO: Move this to the application or somewhere better static
    static LivePrototypeFactory factory = new LivePrototypeFactory();

    @Before
    public void setupTests() {
        TestUtils.initializeStaticTestStorage();
    }
    
    @Test
    public void testKeyRecordParse() {
        TestUtils.processResourceTransaction("resources/inputs/key_record_create.xml");

    }
}
