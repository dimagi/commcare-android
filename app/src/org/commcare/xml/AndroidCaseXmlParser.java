/**
 * 
 */
package org.commcare.xml;

import org.commcare.android.database.SqlIndexedStorageUtility;
import org.commcare.android.database.user.models.ACase;
import org.commcare.cases.model.Case;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.kxml2.io.KXmlParser;

/**
 * @author ctsims
 *
 */
public class AndroidCaseXmlParser extends CaseXmlParser {
	public AndroidCaseXmlParser(KXmlParser parser, IStorageUtilityIndexed storage) {
		super(parser, storage);
	}
	

	public AndroidCaseXmlParser(KXmlParser parser, int[] tallies, boolean b, SqlIndexedStorageUtility<ACase> storage) {
		super(parser, tallies, b, storage);
	}


	/* (non-Javadoc)
	 * @see org.commcare.xml.CaseXmlParser#CreateCase(java.lang.String, java.lang.String)
	 */
	@Override
	protected Case CreateCase(String name, String typeId) {
		return new ACase(name, typeId);
	}
}
