/**
 * 
 */
package org.commcare.xml;

import java.util.Collection;
import java.util.Hashtable;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.ACase;
import org.commcare.cases.model.Case;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.kxml2.io.KXmlParser;

import android.content.Context;

/**
 * @author ctsims
 *
 */
public class CommCareTransactionParserFactory implements TransactionParserFactory {

	private Context context;
	private TransactionParserFactory userParser;
	private TransactionParserFactory caseParser;
	private TransactionParserFactory formInstanceParser;
	
	private Hashtable<String, String> formInstanceNamespaces;
	
	int requests = 0;
	
	public CommCareTransactionParserFactory(Context context) {
		this.context = context;
	}
	
	/* (non-Javadoc)
	 * @see org.commcare.data.xml.TransactionParserFactory#getParser(java.lang.String, java.lang.String, org.kxml2.io.KXmlParser)
	 */
	public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
		if(namespace != null && formInstanceNamespaces != null && formInstanceNamespaces.containsKey(namespace)) {
			req();
			return formInstanceParser.getParser(name, namespace, parser);
		} else if(name != null && name.toLowerCase().equals("case")) {
			if(caseParser == null) {
				throw new RuntimeException("Couldn't recieve Case transaction without initialization!");
			}
			req();
			return caseParser.getParser(name, namespace, parser);
		} else if(name != null && name.toLowerCase().equals("registration")) {
			if(userParser == null) {
				throw new RuntimeException("Couldn't recieve User transaction without initialization!");
			}
			req();
			return userParser.getParser(name, namespace, parser);
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
	
	public void initCaseParser(final Collection<String> existingCases) {
		final int[] tallies = new int[3];
		caseParser = new TransactionParserFactory() {
			CaseXmlParser created = null;
			
			public TransactionParser<Case> getParser(String name, String namespace, KXmlParser parser) {
				if(created == null) {
					created = new AndroidCaseXmlParser(parser, tallies, true, CommCareApplication._().getStorage(ACase.STORAGE_KEY, ACase.class));
				}
				
				return created;
			}
		};
	}
	
	public void initFormInstanceParser(Hashtable<String, String> namespaces) {
		this.formInstanceNamespaces = namespaces;
		formInstanceParser = new TransactionParserFactory() {
			FormInstanceXmlParser created = null;
			
			public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
				if(created == null) {
					created = new FormInstanceXmlParser(parser, context, formInstanceNamespaces);
				}
				
				return created;
			}
		};
	}

}
