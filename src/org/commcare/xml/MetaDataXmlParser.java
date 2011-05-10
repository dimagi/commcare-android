/**
 * 
 */
package org.commcare.xml;

import java.io.IOException;
import java.util.Date;

import org.commcare.data.xml.TransactionParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.javarosa.core.model.utils.DateUtils;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * @author ctsims
 *
 */
public class MetaDataXmlParser extends TransactionParser<Date> {

	public MetaDataXmlParser(KXmlParser parser) {
		super(parser, "meta", null);
	}

	@Override
	public void commit(Date formEditDate) throws IOException {
		//nothing;
	}

	@Override
	public Date parse() throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
		this.checkNode("meta");
		
		Date timeend = null;
		
		while(this.nextTagInBlock("meta")) {
			String item = this.parser.getName();
			if(item == null) { continue;}
			if("timeend".equals(item.toLowerCase())) {
				String dateModified = parser.nextText().trim();
				timeend = DateUtils.parseDateTime(dateModified);
				commit(timeend);
			}
		}
		return null;
	}

}
