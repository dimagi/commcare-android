package org.commcare.xml;

import android.content.Context;

import org.commcare.android.database.user.models.ACase;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.model.Case;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Collections;
import java.util.Hashtable;

/**
 * The CommCare Transaction Parser Factory wraps all of the current
 * transactions that CommCare knows about, and provides the appropriate hooks
 * for parsing through XML and dispatching the right handler for each
 * transaction.
 *
 * It should be the central point of processing for transactions (eliminating
 * the use of the old datamodel based processors) and should be used in any
 * situation where a transaction is expected to be present.
 *
 * It is expected to behave more or less as a black box, in that it directly
 * creates/modifies the data models on the system, rather than producing them
 * for another layer or processing.
 *
 * @author ctsims
 */

public class CommCareTransactionParserFactory implements TransactionParserFactory {

    private Context context;
    private TransactionParserFactory userParser;
    private TransactionParserFactory caseParser;
    private TransactionParserFactory stockParser;
    private TransactionParserFactory formInstanceParser;
    private TransactionParserFactory fixtureParser;

    /**
     * A mapping from an installed form's namespace its install path.
     */
    private Hashtable<String, String> formInstanceNamespaces;

    HttpRequestGenerator generator;

    int requests = 0;
    String syncToken;
    
    public CommCareTransactionParserFactory(Context context, HttpRequestGenerator generator) {
        this.context = context;
        this.generator = generator;
        fixtureParser = new TransactionParserFactory() {
            FixtureXmlParser created = null;

            @Override
            public TransactionParser getParser(KXmlParser parser) {
                if(created == null) {
                    created = new FixtureXmlParser(parser) {
                        //TODO: store these on the file system instead of in DB?
                        private IStorageUtilityIndexed fixtureStorage;
                        
                        @Override
                        public IStorageUtilityIndexed storage() {
                            if(fixtureStorage == null) {
                                fixtureStorage = CommCareApplication._().getUserStorage("fixture", FormInstance.class);
                            } 
                            return fixtureStorage;
                        }
                    };
                }
                
                return created;
            }
        }; 
    }
    
    @Override
    public TransactionParser getParser(KXmlParser parser) {
        String name = parser.getName();
        String namespace = parser.getNamespace();
        if (namespace != null && formInstanceNamespaces != null && formInstanceNamespaces.containsKey(namespace)) {
            req();
            return formInstanceParser.getParser(parser);
        } else if(LedgerXmlParsers.STOCK_XML_NAMESPACE.matches(namespace)) {
            if(stockParser == null) {
                throw new RuntimeException("Couldn't process Stock transaction without initialization!");
            }
            req();
            return stockParser.getParser(parser);
        } else if("case".equalsIgnoreCase(name)) {
            if(caseParser == null) {
                throw new RuntimeException("Couldn't receive Case transaction without initialization!");
            }
            req();
            return caseParser.getParser(parser);
        } else if ("registration".equalsIgnoreCase(name)) {
            if(userParser == null) {
                throw new RuntimeException("Couldn't receive User transaction without initialization!");
            }
            req();
            return userParser.getParser(parser);
        } else if ("fixture".equalsIgnoreCase(name)) {
            req();
            return fixtureParser.getParser(parser);
        } else if ("message".equalsIgnoreCase(name)) {
            //server message;
            //" <message nature=""/>"
        } else if("sync".equalsIgnoreCase(name) && "http://commcarehq.org/sync".equals(namespace)) {
            return new TransactionParser<String>(parser) {
                /*
                 * (non-Javadoc)
                 * @see org.commcare.data.xml.TransactionParser#commit(java.lang.Object)
                 */
                @Override
                public void commit(String parsed) throws IOException {}

                @Override
                public String parse() throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
                    this.checkNode("sync");
                    this.nextTag("restore_id");
                    syncToken = parser.nextText();
                    if(syncToken == null) {
                        throw new InvalidStructureException("Sync block must contain restore_id with valid ID inside!", parser);
                    }
                    syncToken = syncToken.trim();
                    return syncToken;
                }
            };
        }
        return null;
    }
    
    private void req() {
        requests++;
        reportProgress(requests);
    }
    
    public void reportProgress(int total) {
        //nothing
    }
    
    public void initUserParser(final byte[] wrappedKey) {
        userParser = new TransactionParserFactory() {
            UserXmlParser created = null;

            @Override
            public TransactionParser getParser(KXmlParser parser) {
                if(created == null) {
                    created = new UserXmlParser(parser, context, wrappedKey);
                }
                
                return created;
            }
        };
    }
    
    public void initCaseParser() {
        final int[] tallies = new int[3];
        caseParser = new TransactionParserFactory() {
            CaseXmlParser created = null;

            @Override
            public TransactionParser<Case> getParser(KXmlParser parser) {
                if(created == null) {
                    created = new AndroidCaseXmlParser(parser, tallies, true, CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class), generator);
                }
                
                return created;
            }
        };
    }
    
    public void initStockParser() {
        stockParser = new TransactionParserFactory() {
            
            public TransactionParser<Ledger[]> getParser(KXmlParser parser) {
                return new LedgerXmlParsers(parser, CommCareApplication._().getUserStorage(Ledger.STORAGE_KEY, Ledger.class));
            }
        };
    }

    /**
     * @param namespaces A mapping from an installed form's namespace its install path.
     */
    public void initFormInstanceParser(Hashtable<String, String> namespaces) {
        this.formInstanceNamespaces = namespaces;

        formInstanceParser = new TransactionParserFactory() {
            FormInstanceXmlParser created = null;

            @Override
            public TransactionParser getParser(KXmlParser parser) {
                if (created == null) {
                    //TODO: We really don't wanna keep using fsPath eventually
                    created = new FormInstanceXmlParser(parser, context,
                            Collections.unmodifiableMap(formInstanceNamespaces),
                            CommCareApplication._().getCurrentApp().fsPath(GlobalConstants.FILE_CC_FORMS));
                }

                return created;
            }
        };
    }

    public String getSyncToken() {
        return syncToken;
    }
}
