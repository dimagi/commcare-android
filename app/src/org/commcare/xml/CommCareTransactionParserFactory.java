package org.commcare.xml;

import java.io.IOException;
import java.util.Hashtable;

import org.commcare.android.database.user.models.ACase;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.cases.ledger.Ledger;
import org.commcare.cases.model.Case;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;

/**
 * @author ctsims
 *
 */
public class CommCareTransactionParserFactory implements TransactionParserFactory {

    private Context context;
    private TransactionParserFactory userParser;
    private TransactionParserFactory caseParser;
    private TransactionParserFactory stockParser;
    private TransactionParserFactory formInstanceParser;
    private TransactionParserFactory fixtureParser;
    
    private Hashtable<String, String> formInstanceNamespaces;
    HttpRequestGenerator generator;
    
    int requests = 0;
    String syncToken;
    
    public CommCareTransactionParserFactory(Context context, HttpRequestGenerator generator) {
        this.context = context;
        this.generator = generator;
        fixtureParser = new TransactionParserFactory() {
            FixtureXmlParser created = null;
            
            public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
                if(created == null) {
                    created = new FixtureXmlParser(parser) {
                        //TODO: store these on the file system instead of in DB?
                        private IStorageUtilityIndexed fixtureStorage;
                        
                        /*
                         * (non-Javadoc)
                         * @see org.commcare.xml.FixtureXmlParser#storage()
                         */
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
    
    
    /* (non-Javadoc)
     * @see org.commcare.data.xml.TransactionParserFactory#getParser(java.lang.String, java.lang.String, org.kxml2.io.KXmlParser)
     */
    public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
        if(namespace != null && formInstanceNamespaces != null && formInstanceNamespaces.containsKey(namespace)) {
            req();
            return formInstanceParser.getParser(name, namespace, parser);
        } else if(LedgerXmlParsers.STOCK_XML_NAMESPACE.matches(namespace)) {
            if(stockParser == null) {
                throw new RuntimeException("Couldn't process Stock transaction without initialization!");
            }
            req();
            return stockParser.getParser(name, namespace, parser);
        } else if(name != null && name.toLowerCase().equals("case")) {
            if(caseParser == null) {
                throw new RuntimeException("Couldn't receive Case transaction without initialization!");
            }
            req();
            return caseParser.getParser(name, namespace, parser);
        } else if(name != null && name.toLowerCase().equals("registration")) {
            if(userParser == null) {
                throw new RuntimeException("Couldn't receive User transaction without initialization!");
            }
            req();
            return userParser.getParser(name, namespace, parser);
        } else if(name != null && name.toLowerCase().equals("fixture")) {
            req();
            return fixtureParser.getParser(name, namespace, parser);
        }else if(name != null && name.toLowerCase().equals("message")) {
            //server message;
            //" <message nature=""/>"
        } else if(name != null && name.toLowerCase().equals("sync") && namespace != null && "http://commcarehq.org/sync".equals(namespace)) {
            return new TransactionParser<String>(parser, namespace, namespace) {
                /*
                 * (non-Javadoc)
                 * @see org.commcare.data.xml.TransactionParser#commit(java.lang.Object)
                 */
                @Override
                public void commit(String parsed) throws IOException {}

                /*
                 * (non-Javadoc)
                 * @see org.javarosa.xml.ElementParser#parse()
                 */
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
            
            public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
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
            
            public TransactionParser<Case> getParser(String name, String namespace, KXmlParser parser) {
                if(created == null) {
                    created = new AndroidCaseXmlParser(parser, tallies, true, CommCareApplication._().getUserStorage(ACase.STORAGE_KEY, ACase.class), generator);
                }
                
                return created;
            }
        };
    }
    
    public void initStockParser() {
        stockParser = new TransactionParserFactory() {
            
            public TransactionParser<Ledger[]> getParser(String name, String namespace, KXmlParser parser) {
                return new LedgerXmlParsers(parser, CommCareApplication._().getUserStorage(Ledger.STORAGE_KEY, Ledger.class));
            }
        };
    }
    
    public void initFormInstanceParser(Hashtable<String, String> namespaces) {
        this.formInstanceNamespaces = namespaces;
        formInstanceParser = new TransactionParserFactory() {
            FormInstanceXmlParser created = null;
            
            public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
                if(created == null) {
                    //TODO: We really don't wanna keep using fsPath eventually
                    created = new FormInstanceXmlParser(parser, context, formInstanceNamespaces, CommCareApplication._().getCurrentApp().fsPath(GlobalConstants.FILE_CC_FORMS));
                }
                
                return created;
            }
        };
    }

    public String getSyncToken() {
        return syncToken;
    }
}
