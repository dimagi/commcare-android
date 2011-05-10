/**
 * 
 */
package org.commcare.xml;

import java.util.Collection;
import java.util.Hashtable;

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
	
	public CommCareTransactionParserFactory(Context context) {
		this.context = context;
	}
	
	/* (non-Javadoc)
	 * @see org.commcare.data.xml.TransactionParserFactory#getParser(java.lang.String, java.lang.String, org.kxml2.io.KXmlParser)
	 */
	public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
		if(namespace == null) {
			int a  =3;
			a++;
		}
		
		if(namespace != null && formInstanceNamespaces != null && formInstanceNamespaces.containsKey(namespace)) {
			return formInstanceParser.getParser(name, namespace, parser);
		} else if("case".toLowerCase().equals(name)) {
			if(caseParser == null) {
				throw new RuntimeException("Couldn't recieve Case transaction without initialization!");
			}
			return userParser.getParser(name, namespace, parser);
		} else if("registration".toLowerCase().equals(name)) {
			if(userParser == null) {
				throw new RuntimeException("Couldn't recieve User transaction without initialization!");
			}
			return userParser.getParser(name, namespace, parser);
		}
		return null;
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
		caseParser = new TransactionParserFactory() {
			CaseXmlParser created = null;
			
			public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
				if(created == null) {
					created = new CaseXmlParser(parser, context, existingCases);
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
