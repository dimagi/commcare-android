/**
 * 
 */
package org.commcare.xml;

import java.io.IOException;
import java.util.Date;
import java.util.NoSuchElementException;

import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.models.Case;
import org.commcare.data.xml.TransactionParser;
import org.commcare.xml.util.InvalidStructureException;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;

/**
 * @author ctsims
 *
 */
public class CaseXmlParser extends TransactionParser<Case> {

	Context c;
	IStorageUtilityIndexed storage;
	
	public CaseXmlParser(KXmlParser parser, Context c) {
		super(parser, "case", null);
		this.c = c;
	}

	public Case parse() throws InvalidStructureException, IOException, XmlPullParserException {
		this.checkNode("case");
		
		//parse (with verification) the next tag
		this.nextTag("case_id");
		String caseId = parser.nextText();
		
		this.nextTag("date_modified");
		String dateModified = parser.nextText();
		Date modified = DateUtils.parseDateTime(dateModified);
		
		//Now look for actions
		while(this.nextTagInBlock("case")) {
			
			String action = parser.getName().toLowerCase();
			
			if(action.equals("create")) {
				String[] data = new String[4];
				//Collect all data
				while(this.nextTagInBlock("create")) {
					if(parser.getName().equals("case_type_id")) {
						data[0] = parser.nextText();
					} else if(parser.getName().equals("external_id")) {
						data[1] = parser.nextText();
					} else if(parser.getName().equals("user_id")) {
						data[2] = parser.nextText();
					} else if(parser.getName().equals("case_name")) {
						data[3] = parser.nextText();
					} else {
						throw new InvalidStructureException("Expected one of [case_type_id, external_id, user_id, case_name], found " + parser.getName(), parser);
					}
				}
				
				//Verify that we got all the pieces
				for(String s : data) {
					if(s == null) {
						throw new InvalidStructureException("One of [case_type_id, external_id, user_id, case_name] is missing for case <create> with ID: " + caseId, parser);
					}
				}
				
				//Create the case.
				Case c = new Case(data[3], data[0]);
				c.setUserId(this.parseInt(data[2]));
				c.setExternalId(data[1]);
				c.setCaseId(caseId);
				commit(c);
				
			} else if(action.equals("update")) {
				Case c = retrieve(caseId);
				while(this.nextTagInBlock("update")) {
					String key = parser.getName();
					String value = parser.nextText();
					c.setProperty(key,value);
				}
				commit(c);
			} else if(action.equals("close")) {
				Case c = retrieve(caseId);
				c.setClosed(true);
				commit(c);
			} else if(action.equals("referral")) {
				new ReferralXmlParser(parser,caseId,modified, c).parse();
			}
		}
		
		
		return null;
	}

	public void commit(Case parsed) throws IOException {
		try {
			storage().write(parsed);
		} catch (StorageFullException e) {
			e.printStackTrace();
			throw new IOException("Storage full while writing case!");
		}
	}

	public Case retrieve(String entityId) {
		IStorageUtilityIndexed storage = storage();
		try{
			return (Case)storage.getRecordForValue("caseid", entityId);
		} catch(NoSuchElementException nsee) {
			return null;
		}
	}
	
	public IStorageUtilityIndexed storage() {
		if(storage == null) {
			storage = new SqlIndexedStorageUtility(Case.STORAGE_KEY, Case.class.getName(), c);
		} 
		return storage;
	}

}
