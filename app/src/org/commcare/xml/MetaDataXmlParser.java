package org.commcare.xml;

import java.io.IOException;

import org.commcare.android.util.Base64;
import org.commcare.data.xml.TransactionParser;
import org.commcare.xml.util.InvalidStructureException;
import org.commcare.xml.util.UnfullfilledRequirementsException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * @author ctsims
 *
 */
public class MetaDataXmlParser extends TransactionParser<String[]> {

    public MetaDataXmlParser(KXmlParser parser) {
        super(parser, "meta", null);
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.data.xml.TransactionParser#commit(java.lang.Object)
     */
    @Override
    public void commit(String[] data) throws IOException {
        //nothing;
    }

    /*
     * (non-Javadoc)
     * @see org.commcare.xml.ElementParser#parse()
     */
    @Override
    public String[] parse() throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
        this.checkNode("meta");
        
        String lastModified = null;
        String uid = null;
        
        while(this.nextTagInBlock("meta")) {
            String item = this.parser.getName();
            if(item == null) { continue;}
            if("timestart".equals(item.toLowerCase())) {
                String start = parser.nextText().trim();
                //Only update modified if time end hasn't set it
                if(lastModified == null || lastModified == "") {
                    lastModified = start;
                }
            }
            else if("timeend".equals(item.toLowerCase())) {
                String end = parser.nextText().trim();
                if(end != null && end != "") {
                    lastModified = end;
                }
            }
            
            //Ugh. this will make this 2.0 and 1.N compatible, which isn't awesome, but we'll split out versioning later...
            else if("uid".equals(item.toLowerCase()) || "instanceid".equals(item.toLowerCase())) {
                uid = parser.nextText().trim();
            }
        }
        String[] ret = new String[] {lastModified, uid};
        commit(ret);
        return ret;
    }

}
