package org.commcare.xml;

import android.content.Context;

import org.commcare.android.database.AndroidSandbox;
import org.commcare.android.logic.GlobalConstants;
import org.commcare.android.net.HttpRequestGenerator;
import org.commcare.cases.model.Case;
import org.commcare.core.parse.CommCareTransactionParserFactory;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.kxml2.io.KXmlParser;

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

public class AndroidTransactionParserFactory extends CommCareTransactionParserFactory {

    final private Context context;
    final private HttpRequestGenerator generator;

    private TransactionParserFactory formInstanceParser;
    private boolean caseIndexesWereDisrupted = false;

    /**
     * A mapping from an installed form's namespace its install path.
     */
    private Hashtable<String, String> formInstanceNamespaces;

    public AndroidTransactionParserFactory(Context context, HttpRequestGenerator generator) {
        super(new AndroidSandbox(CommCareApplication._()));
        this.context = context;
        this.generator = generator;
    }

    @Override
    public TransactionParser getParser(KXmlParser parser) {
        String namespace = parser.getNamespace();
        if (namespace != null && formInstanceNamespaces != null && formInstanceNamespaces.containsKey(namespace)) {
            req();
            return formInstanceParser.getParser(parser);
        }
        return super.getParser(parser);
    }

    /*
     * need to override to use the Android User class
     */
    public void initUserParser(final byte[] wrappedKey) {
        userParser = new TransactionParserFactory() {
            AndroidUserXmlParser created = null;

            @Override
            public TransactionParser getParser(KXmlParser parser) {
                if (created == null) {
                    created = new AndroidUserXmlParser(parser, sandbox.getUserStorage(), wrappedKey);
                }

                return created;
            }
        };
    }

    @Override
    public void initCaseParser() {
        final int[] tallies = new int[3];
        caseParser = new TransactionParserFactory() {
            CaseXmlParser created = null;

            @Override
            public TransactionParser<Case> getParser(KXmlParser parser) {
                if (created == null) {
                    created = new AndroidCaseXmlParser(parser, tallies, true, sandbox.getCaseStorage(), generator) {

                        @Override
                        public void onIndexDisrupted(String caseId) {
                            caseIndexesWereDisrupted = true;
                        }
                    };
                }

                return created;
            }
        };
    }


    /**
     * Used to load Saved Forms from HQ into our local file system
     *
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

    public boolean wereCaseIndexesDisrupted() {
        return caseIndexesWereDisrupted;
    }
}
