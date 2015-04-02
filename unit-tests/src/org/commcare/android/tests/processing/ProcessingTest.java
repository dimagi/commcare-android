/**
 * 
 */
package org.commcare.android.tests.processing;

import static junit.framework.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.database.ConcreteDbHelper;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.CommCareUserOpenHelper;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.CaseIndexTable;
import org.commcare.android.database.user.models.EntityStorageCache;
import org.commcare.android.junit.CommCareTestRunner;
import org.commcare.android.shadows.SQLiteDatabaseNative;
import org.commcare.android.util.AndroidUtil;
import org.commcare.android.util.LivePrototypeFactory;
import org.commcare.cases.model.Case;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.AndroidCaseXmlParser;
import org.commcare.xml.CaseXmlParser;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kxml2.io.KXmlParser;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParserException;

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
        
        //Sets the static strategy for the deserializtion code to be
        //based on an optimized md5 hasher. Major speed improvements.
        PrototypeFactory.setStaticHasher(factory);
        AndroidUtil.initializeStaticHandlers();
        
    }
    
    @Test
    public void testIndexRemoval() {
        processResourceTransaction("resources/inputs/case_create.xml");
        Case c = getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id");
        assertEquals("Case Name", "Test Case", c.getName());
        assertEquals("Case Property", "initial", c.getPropertyString("test_value"));
        
        processResourceTransaction("resources/inputs/case_update.xml");
        Case c2 = getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id");
        assertEquals("Updated", "changed", c2.getPropertyString("test_value"));
        
        processResourceTransaction("resources/inputs/case_create_and_index.xml");
        Case c3 = getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id_child");
        assertEquals("Indexed", "test_case_id", c3.getIndices().elementAt(0).getTarget());

        processResourceTransaction("resources/inputs/case_break_index.xml");
        Case c4 = getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id_child");
        assertEquals("Removed Index Count", 0, c4.getIndices().size());
    }
    
    @Test
    public void testTypeChange() {
        processResourceTransaction("resources/inputs/case_create.xml");
        Case c = getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id");
        assertEquals("Initial Type", "unit_test", c.getTypeId());
        
        processResourceTransaction("resources/inputs/case_change_type.xml");
        Case c2 = getCaseStorage().getRecordForValue(ACase.INDEX_CASE_ID, "test_case_id");
        assertEquals("Changed Type", "changed_unit_test", c2.getTypeId());
    }


    private TransactionParserFactory getFactory(final SQLiteDatabase db) {
        return new TransactionParserFactory() {
            
            public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
                if(CaseXmlParser.CASE_XML_NAMESPACE.equals(namespace) && name.toLowerCase().equals("case")) {
                    return new AndroidCaseXmlParser(parser, getCaseStorage(db), new EntityStorageCache("case", db), new CaseIndexTable(db)) {
                        @Override
                        protected SQLiteDatabase getDbHandle() {
                            return db;
                        }
                    };
                } 
                return null;
            }
            
        };
        
    }
    
    protected void processResourceTransaction(String resourcePath) {
        CommCareUserOpenHelper helper = new CommCareUserOpenHelper(Robolectric.application, "Test");
        final SQLiteDatabase db = helper.getWritableDatabase("Test");

        DataModelPullParser parser;
        
        try{
            InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
            parser = new DataModelPullParser(is, getFactory(db), true, true);
            parser.parse();
            is.close();
        
        } catch(IOException ioe) {
            throw wrap(ioe, "IO Error parsing transactions");
        } catch (InvalidStructureException e) {
            throw wrap(e, "Bad Transaction");
        } catch (XmlPullParserException e) {
            throw wrap(e, "Bad XML");
        } catch (UnfullfilledRequirementsException e) {
            throw wrap(e, "Bad State");
        }
    }
    
    protected SqlStorage<ACase> getCaseStorage() {
        CommCareUserOpenHelper helper = new CommCareUserOpenHelper(Robolectric.application, "Test");
        final SQLiteDatabase db = helper.getWritableDatabase("Test");

        return getCaseStorage(db);
    }

    protected SqlStorage<ACase> getCaseStorage(SQLiteDatabase db) {
        
        return new SqlStorage<ACase>(ACase.STORAGE_KEY, ACase.class, new ConcreteDbHelper(Robolectric.application, db) {

            @Override
            public PrototypeFactory getPrototypeFactory() {
                return factory;
            }
               
        });
    }
    
    public static RuntimeException wrap(Exception e, String prefix) {
        e.printStackTrace();
        RuntimeException re = new RuntimeException(prefix + ": " + e.getMessage());
        re.initCause(e);
        return re;
    }
}
