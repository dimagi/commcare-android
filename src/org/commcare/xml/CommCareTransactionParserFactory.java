/**
 * 
 */
package org.commcare.xml;

import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.models.ACase;
import org.commcare.cases.model.Case;
import org.commcare.data.xml.TransactionParser;
import org.commcare.data.xml.TransactionParserFactory;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
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
	private TransactionParserFactory formInstanceParser;
	private TransactionParserFactory fixtureParser;
	
	private Hashtable<String, String> formInstanceNamespaces;
	
	int requests = 0;
	String syncToken;
	
	public CommCareTransactionParserFactory(Context context) {
		this.context = context;
		fixtureParser = new TransactionParserFactory() {
			FixtureXmlParser created = null;
			
			public TransactionParser getParser(String name, String namespace, KXmlParser parser) {
				if(created == null) {
					created = new FixtureXmlParser(parser) {
						//TODO: store these on the file system instead of in DB?
						private IStorageUtilityIndexed fixtureStorage;
						public IStorageUtilityIndexed storage() {
							if(fixtureStorage == null) {
								fixtureStorage = CommCareApplication._().getStorage("fixture", FormInstance.class);
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

	public String getSyncToken() {
		return syncToken;
	}
}
