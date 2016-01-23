package org.commcare.android.util;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.cases.AndroidCaseInstanceTreeElement;
import org.commcare.android.database.ConcreteAndroidDbHelper;
import org.commcare.android.database.DbUtil;
import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.DatabaseUserOpenHelper;
import org.commcare.android.database.user.models.ACase;
import org.commcare.android.database.user.models.CaseIndexTable;
import org.commcare.android.database.user.models.EntityStorageCache;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.storage.FormSaveUtil;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.util.externalizable.AndroidClassHasher;
import org.commcare.xml.AndroidCaseXmlParser;
import org.commcare.xml.AndroidTransactionParserFactory;
import org.commcare.xml.CaseXmlParser;
import org.commcare.xml.FormInstanceXmlParser;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
import org.robolectric.RuntimeEnvironment;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Hashtable;

/**
 * @author ctsims
 */
public class TestUtils {
    
    //TODO: Move this to the application or somewhere better static

    /**
     * Initialize all of the static hooks we need to make storage possible
     * in the mocked/shadow world 
     */
    public static void initializeStaticTestStorage() {
        //Sets the static strategy for the deserializtion code to be
        //based on an optimized md5 hasher. Major speed improvements.
        DbUtil.setDBUtilsPrototypeFactory(new LivePrototypeFactory(new AndroidClassHasher()));
        AndroidUtil.initializeStaticHandlers();
        
        // For now, disable the optimizations, since they require in-depth SQL code that
        // we need better shadows for 
        SqlStorage.STORAGE_OPTIMIZATIONS_ACTIVE = false;
    }
    

    /**
     * Get a form instance and case enabled parsing factory
     */
    private static TransactionParserFactory getFactory(final SQLiteDatabase db) {
        final Hashtable<String, String> formInstanceNamespaces;
        if (CommCareApplication._().getCurrentApp() != null) {
            formInstanceNamespaces = FormSaveUtil.getNamespaceToFilePathMap(CommCareApplication._());
        } else {
            formInstanceNamespaces = null;
        }
        return new TransactionParserFactory() {
            @Override
            public TransactionParser getParser(KXmlParser parser) {
                String namespace = parser.getNamespace();
                if (namespace != null && formInstanceNamespaces != null && formInstanceNamespaces.containsKey(namespace)) {
                    return new FormInstanceXmlParser(parser, CommCareApplication._(),
                            Collections.unmodifiableMap(formInstanceNamespaces),
                            CommCareApplication._().getCurrentApp().fsPath(GlobalConstants.FILE_CC_FORMS));
                } else if(CaseXmlParser.CASE_XML_NAMESPACE.equals(parser.getNamespace()) && "case".equalsIgnoreCase(parser.getName())) {
                    return new AndroidCaseXmlParser(parser, getCaseStorage(db), new EntityStorageCache("case", db), new CaseIndexTable(db)) {
                        protected SQLiteDatabase getDbHandle() {
                            return db;
                        }
                    };
                } 
                return null;
            }
            
        };
        
    }

    /**
     * Process an input XML file for transactions and update the relevant databases.
     */
    public static void processResourceTransaction(String resourcePath) {
        final SQLiteDatabase db = getTestDb();

        DataModelPullParser parser;

        try{
            InputStream is = System.class.getResourceAsStream(resourcePath);

            parser = new DataModelPullParser(is, getFactory(db), true, true);
            parser.parse();
            is.close();

        } catch(IOException ioe) {
            throw wrapError(ioe, "IO Error parsing transactions");
        } catch (InvalidStructureException e) {
            throw wrapError(e, "Bad Transaction");
        } catch (XmlPullParserException e) {
            throw wrapError(e, "Bad XML");
        } catch (UnfullfilledRequirementsException e) {
            throw wrapError(e, "Bad State");
        }
    }

    public static void processResourceTransactionIntoAppDb(String resourcePath) {
        DataModelPullParser parser;

        AndroidTransactionParserFactory androidTransactionFactory =
                new AndroidTransactionParserFactory(CommCareApplication._().getApplicationContext(), null);

        if (CommCareApplication._().getCurrentApp() != null) {
            Hashtable<String, String> formInstanceNamespaces =
                    FormSaveUtil.getNamespaceToFilePathMap(CommCareApplication._());
            androidTransactionFactory.initFormInstanceParser(formInstanceNamespaces);
        }

        try{
            InputStream is = System.class.getResourceAsStream(resourcePath);
            
            parser = new DataModelPullParser(is, androidTransactionFactory, true, true);
            parser.parse();
            is.close();
        
        } catch(IOException ioe) {
            throw wrapError(ioe, "IO Error parsing transactions");
        } catch (InvalidStructureException e) {
            throw wrapError(e, "Bad Transaction");
        } catch (XmlPullParserException e) {
            throw wrapError(e, "Bad XML");
        } catch (UnfullfilledRequirementsException e) {
            throw wrapError(e, "Bad State");
        }
    }
    
    /**
     * @return The hook for the test user-db 
     */
    private static SQLiteDatabase getTestDb() {
        DatabaseUserOpenHelper helper = new DatabaseUserOpenHelper(RuntimeEnvironment.application, "Test");
        return helper.getWritableDatabase("Test");
    }

    public static PrototypeFactory getStaticPrototypeFactory(){
        return DbUtil.getPrototypeFactory(RuntimeEnvironment.application);
    }
    
    /**
     * @return A test-db case storage object
     */
    public static SqlStorage<ACase> getCaseStorage() {
        return getCaseStorage(getTestDb());
    }

    /**
     * @return The case storage object for the provided db
     */
    private static SqlStorage<ACase> getCaseStorage(SQLiteDatabase db) {

            return new SqlStorage<>(ACase.STORAGE_KEY, ACase.class, new ConcreteAndroidDbHelper(RuntimeEnvironment.application, db) {
                @Override
                public PrototypeFactory getPrototypeFactory() {
                    return getStaticPrototypeFactory();
                }

            });
    }
    
    //TODO: Make this work natively with the CommCare Android IIF
    /**
     * @return An evaluation context which is capable of evaluating against
     * the connected storage instances: casedb is the only one supported for now
     */
    public static EvaluationContext getInstanceBackedEvaluationContext() {
        final SQLiteDatabase db = getTestDb();
        
        AndroidInstanceInitializer iif = new AndroidInstanceInitializer() {
            @Override
            public AbstractTreeElement setupCaseData(ExternalDataInstance instance) {
                SqlStorage<ACase> storage = getCaseStorage(db);
                AndroidCaseInstanceTreeElement casebase = new AndroidCaseInstanceTreeElement(instance.getBase(), storage, false, new CaseIndexTable(db));
                instance.setCacheHost(casebase);
                return casebase;
            }
        };

        ExternalDataInstance edi = new ExternalDataInstance("jr://instance/casedb", "casedb");
        DataInstance specializedDataInstance = edi.initialize(iif, "casedb");
        
        Hashtable<String, DataInstance> formInstances = new Hashtable<>();
        formInstances.put("casedb", specializedDataInstance);
        
        TreeReference dummy = TreeReference.rootRef().extendRef("a", TreeReference.DEFAULT_MUTLIPLICITY);
        return new EvaluationContext(new EvaluationContext(null), formInstances, dummy);
    }

    public static EvaluationContext getEvaluationContextWithAndroidIIF() {
        AndroidInstanceInitializer iif = new AndroidInstanceInitializer(CommCareApplication._().getCurrentSession());
        ExternalDataInstance edi = new ExternalDataInstance("jr://instance/casedb", "casedb");
        DataInstance specializedDataInstance = edi.initialize(iif, "casedb");

        Hashtable<String, DataInstance> formInstances = new Hashtable<String, DataInstance>();
        formInstances.put("casedb", specializedDataInstance);

        TreeReference dummy = TreeReference.rootRef().extendRef("a", TreeReference.DEFAULT_MUTLIPLICITY);
        EvaluationContext ec = new EvaluationContext(new EvaluationContext(null), formInstances, dummy);
        return ec;
    }


    public static RuntimeException wrapError(Exception e, String prefix) {
        e.printStackTrace();
        RuntimeException re = new RuntimeException(prefix + ": " + e.getMessage());
        re.initCause(e);
        return re;
    }

}
