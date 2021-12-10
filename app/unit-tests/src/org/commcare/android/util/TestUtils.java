package org.commcare.android.util;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.AppUtils;
import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.FormEntryActivity;
import org.commcare.android.database.app.models.FormDefRecord;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.instance.CaseInstanceTreeElement;
import org.commcare.cases.ledger.Ledger;
import org.commcare.core.interfaces.UserSandbox;
import org.commcare.data.xml.DataModelPullParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.engine.cases.AndroidLedgerInstanceTreeElement;
import org.commcare.models.AndroidClassHasher;
import org.commcare.models.database.AndroidPrototypeFactorySetup;
import org.commcare.models.database.ConcreteAndroidDbHelper;
import org.commcare.models.database.SqlStorage;
import org.commcare.models.database.user.DatabaseUserOpenHelper;
import org.commcare.models.database.user.models.AndroidCaseIndexTable;
import org.commcare.models.database.user.models.EntityStorageCache;
import org.commcare.modern.database.TableBuilder;
import org.commcare.test.utilities.CaseTestUtils;
import org.commcare.utils.AndroidInstanceInitializer;
import org.commcare.utils.FormSaveUtil;
import org.commcare.utils.GlobalConstants;
import org.commcare.xml.AndroidBulkCaseXmlParser;
import org.commcare.xml.AndroidCaseXmlParser;
import org.commcare.xml.AndroidTransactionParserFactory;
import org.commcare.xml.CaseXmlParser;
import org.commcare.xml.FormInstanceXmlParser;
import org.commcare.xml.LedgerXmlParsers;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.DataInstance;
import org.javarosa.core.model.instance.ExternalDataInstance;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.storage.Persistable;
import org.javarosa.core.util.externalizable.PrototypeFactory;
import org.javarosa.test_utils.ExprEvalUtils;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.robolectric.RuntimeEnvironment;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Hashtable;

import static org.junit.Assert.assertTrue;

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
        AndroidPrototypeFactorySetup.setDBUtilsPrototypeFactory(new LivePrototypeFactory(AndroidClassHasher.getInstance()));
        disableSqlOptimizations();
    }

    public static void disableSqlOptimizations() {
        // For now, disable the optimizations, since they require in-depth SQL code that
        // we need better shadows for
        SqlStorage.STORAGE_OPTIMIZATIONS_ACTIVE = false;
    }


    /**
     * Get a form instance and case enabled parsing factory
     */
    private static TransactionParserFactory getFactory(final SQLiteDatabase db) {
        return getFactory(db, false);
    }

    /**
     * Get a form instance and case enabled parsing factory
     */
    private static TransactionParserFactory getFactory(final SQLiteDatabase db, final boolean bulkProcessingEnabled) {
        final Hashtable<String, String> formInstanceNamespaces;
        if (CommCareApplication.instance().getCurrentApp() != null) {
            formInstanceNamespaces = FormSaveUtil.getNamespaceToFilePathMap(CommCareApplication.instance().getAppStorage(FormDefRecord.class));
        } else {
            formInstanceNamespaces = null;
        }
        return parser -> {
            String namespace = parser.getNamespace();
            if (namespace != null && formInstanceNamespaces != null && formInstanceNamespaces.containsKey(namespace)) {
                return new FormInstanceXmlParser(parser,
                        Collections.unmodifiableMap(formInstanceNamespaces),
                        CommCareApplication.instance().getCurrentApp().fsPath(GlobalConstants.FILE_CC_FORMS));
            } else if (CaseXmlParser.CASE_XML_NAMESPACE.equals(parser.getNamespace()) && "case".equalsIgnoreCase(parser.getName())) {

                //Note - this isn't even actually bulk processing. since this class is static
                //there's no good lifecycle to manage the bulk processor in, but at least
                //this will validate that the bulk processor works.
                EntityStorageCache entityStorageCache = null;

                if (CommCareApplication.instance().getCurrentApp() != null) {
                    entityStorageCache = new EntityStorageCache("case", db, AppUtils.getCurrentAppId());
                }

                if (bulkProcessingEnabled) {
                    return new AndroidBulkCaseXmlParser(parser, getCaseStorage(db), entityStorageCache, new AndroidCaseIndexTable(db)) {
                        @Override
                        protected SQLiteDatabase getDbHandle() {
                            return db;
                        }
                    };

                } else {
                    return new AndroidCaseXmlParser(parser, getCaseStorage(db), entityStorageCache, new AndroidCaseIndexTable(db)) {
                        @Override
                        protected SQLiteDatabase getDbHandle() {
                            return db;
                        }
                    };
                }


            } else if (LedgerXmlParsers.STOCK_XML_NAMESPACE.equals(namespace)) {
                return new LedgerXmlParsers(parser, getLedgerStorage(db));
            }
            return null;
        };
    }

    /**
     * Process an input XML file for transactions and update the relevant databases.
     */
    public static void processResourceTransaction(String resourcePath) {
        processResourceTransaction(resourcePath, false);
    }

    /**
     * Process an input XML file for transactions and update the relevant databases.
     */
    public static void processResourceTransaction(String resourcePath,
                                                  boolean bulkProcessingEnabled) {
        final SQLiteDatabase db = getTestDb();

        DataModelPullParser parser;

        try {
            InputStream is = System.class.getResourceAsStream(resourcePath);

            parser = new DataModelPullParser(is, getFactory(db, bulkProcessingEnabled), true, true);
            parser.parse();
            is.close();

        } catch (IOException ioe) {
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
                new AndroidTransactionParserFactory(CommCareApplication.instance().getApplicationContext(), null);

        if (CommCareApplication.instance().getCurrentApp() != null) {
            Hashtable<String, String> formInstanceNamespaces =
                    FormSaveUtil.getNamespaceToFilePathMap(CommCareApplication.instance().getAppStorage(FormDefRecord.class));
            androidTransactionFactory.initFormInstanceParser(formInstanceNamespaces);
        }

        try {
            InputStream is = System.class.getResourceAsStream(resourcePath);

            parser = new DataModelPullParser(is, androidTransactionFactory, true, true);
            parser.parse();
            is.close();

        } catch (IOException ioe) {
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

    public static PrototypeFactory getStaticPrototypeFactory() {
        return CommCareTestApplication.instance().getPrototypeFactory(RuntimeEnvironment.application);
    }

    /**
     * @return A test-db case storage object
     */
    public static SqlStorage<ACase> getCaseStorage() {
        return getCaseStorage(getTestDb());
    }

    public static <T extends Persistable> SqlStorage<T> getStorage(String storageKey, Class<T> prototypeModel) {
        SQLiteDatabase db = getTestDb();
        TableBuilder builder = new TableBuilder(storageKey);

        try {
            builder.addData(prototypeModel.newInstance());
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        String tableCreate = builder.getTableCreateString();
        db.execSQL(tableCreate);

        return new SqlStorage<T>(storageKey, prototypeModel, new ConcreteAndroidDbHelper(RuntimeEnvironment.application, db) {
            @Override
            public PrototypeFactory getPrototypeFactory() {
                return getStaticPrototypeFactory();
            }
        });

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

    private static SqlStorage<Ledger> getLedgerStorage(SQLiteDatabase db) {
        return new SqlStorage<>(Ledger.STORAGE_KEY, Ledger.class, new ConcreteAndroidDbHelper(RuntimeEnvironment.application, db) {
            @Override
            public PrototypeFactory getPrototypeFactory() {
                return getStaticPrototypeFactory();
            }
        });
    }

    //TODO: Make this work natively with the CommCare Android IIF

    public static EvaluationContext getEvaluationContextWithAndroidIIF() {
        AndroidInstanceInitializer iif = new AndroidInstanceInitializer(CommCareApplication.instance().getCurrentSession());
        return buildEvaluationContext(iif, null);
    }

    /**
     * @return An evaluation context which is capable of evaluating against
     * the connected storage instances: casedb is the only one supported for now
     */
    public static EvaluationContext getEvaluationContextWithoutSession() {
        return getEvaluationContextWithoutSession(null);
    }

    public static EvaluationContext getEvaluationContextWithoutSession(DataInstance mainInstanceForEC) {
        return buildEvaluationContext(buildTestInstanceInitializer(), mainInstanceForEC);
    }

    private static AndroidInstanceInitializer buildTestInstanceInitializer() {
        final SQLiteDatabase db = getTestDb();

        return new AndroidInstanceInitializer() {
            @Override
            public AbstractTreeElement setupCaseData(ExternalDataInstance instance) {
                SqlStorage<ACase> storage = getCaseStorage(db);
                CaseInstanceTreeElement casebase = new CaseInstanceTreeElement(instance.getBase(), storage, new AndroidCaseIndexTable(db));
                instance.setCacheHost(casebase);
                return casebase;
            }

            @Override
            protected AbstractTreeElement setupLedgerData(ExternalDataInstance instance) {
                SqlStorage<Ledger> storage = getLedgerStorage(db);
                return new AndroidLedgerInstanceTreeElement(instance.getBase(), storage);
            }
        };
    }

    private static EvaluationContext buildEvaluationContext(AndroidInstanceInitializer iif, DataInstance mainInstance) {
        ExternalDataInstance edi = new ExternalDataInstance(CaseTestUtils.CASE_INSTANCE, "casedb");
        DataInstance specializedDataInstance = edi.initialize(iif, "casedb");

        ExternalDataInstance ledgerDataInstanceRaw = new ExternalDataInstance(CaseTestUtils.LEDGER_INSTANCE, "ledgerdb");
        DataInstance ledgerDataInstance = ledgerDataInstanceRaw.initialize(iif, "ledger");

        Hashtable<String, DataInstance> formInstances = new Hashtable<>();
        formInstances.put("casedb", specializedDataInstance);
        formInstances.put("ledger", ledgerDataInstance);

        return new EvaluationContext(new EvaluationContext(mainInstance), formInstances, TreeReference.rootRef());
    }

    public static DataInstance getCaseDbInstance() {
        ExternalDataInstance edi = new ExternalDataInstance(CaseTestUtils.CASE_INSTANCE, "casedb");
        return edi.initialize(buildTestInstanceInitializer(), "casedb");
    }

    public static RuntimeException wrapError(Exception e, String prefix) {
        e.printStackTrace();
        RuntimeException re = new RuntimeException(prefix + ": " + e.getMessage());
        re.initCause(e);
        throw re;
    }

    /**
     * Create an evaluation context with an abstract instance available.
     */
    public static EvaluationContext buildContextWithInstance(UserSandbox sandbox, String instanceId, String instanceRef) {
        Hashtable<String, String> instanceRefToId = new Hashtable<>();
        instanceRefToId.put(instanceRef, instanceId);
        return buildContextWithInstances(sandbox, instanceRefToId);
    }

    /**
     * Create an evaluation context with an abstract instances available.
     */
    private static EvaluationContext buildContextWithInstances(UserSandbox sandbox,
                                                               Hashtable<String, String> instanceRefToId) {
        InstanceInitializationFactory iif = new AndroidInstanceInitializer(null, sandbox, null);

        Hashtable<String, DataInstance> instances = new Hashtable<>();
        for (String instanceRef : instanceRefToId.keySet()) {
            String instanceId = instanceRefToId.get(instanceRef);
            ExternalDataInstance edi = new ExternalDataInstance(instanceRef, instanceId);
            instances.put(instanceId, edi.initialize(iif, instanceId));
        }

        return new EvaluationContext(null, instances);
    }

    public static void assertFormValue(String expr, Object expectedValue){
        FormDef formDef = FormEntryActivity.mFormController.getFormEntryController().getModel().getForm();
        FormInstance instance = formDef.getMainInstance();

        String errorMsg;
        errorMsg = ExprEvalUtils.expectedEval(expr, instance, null, expectedValue, null);
        assertTrue(errorMsg, "".equals(errorMsg));
    }
}
