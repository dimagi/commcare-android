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
public class ProcessingTest {

    //TODO: Move this to the application or somewhere better static
    static LivePrototypeFactory factory = new LivePrototypeFactory();

    @Before
    public void setupTests() {
        TestUtils.initializeStaticTestStorage();
    }
    
    @Test
    public void testIndexRemoval() {
        TestUtils.processResourceTransaction("resources/inputs/case_create.xml");
        Case c = TestUtils.getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id");
        assertEquals("Case Name", "Test Case", c.getName());
        assertEquals("Case Property", "initial", c.getPropertyString("test_value"));
        
        TestUtils.processResourceTransaction("resources/inputs/case_update.xml");
        Case c2 = TestUtils.getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id");
        assertEquals("Updated", "changed", c2.getPropertyString("test_value"));
        
        TestUtils.processResourceTransaction("resources/inputs/case_create_and_index.xml");
        Case c3 = TestUtils.getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id_child");
        assertEquals("Indexed", "test_case_id", c3.getIndices().elementAt(0).getTarget());

        TestUtils.processResourceTransaction("resources/inputs/case_break_index.xml");
        Case c4 = TestUtils.getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id_child");
        assertEquals("Removed Index Count", 0, c4.getIndices().size());
    }
    
    @Test
    public void testTypeChange() {
        TestUtils.processResourceTransaction("resources/inputs/case_create.xml");
        Case c = TestUtils.getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id");
        assertEquals("Initial Type", "unit_test", c.getTypeId());
        
        TestUtils.processResourceTransaction("resources/inputs/case_change_type.xml");
        Case c2 = TestUtils.getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id");
        assertEquals("Changed Type", "changed_unit_test", c2.getTypeId());
    }
}
