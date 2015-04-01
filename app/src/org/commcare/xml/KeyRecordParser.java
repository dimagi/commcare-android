package org.commcare.xml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import org.commcare.android.crypt.CryptUtil;
import org.commcare.android.database.app.models.UserKeyRecord;
import org.commcare.android.util.Base64;
import org.commcare.android.util.Base64DecoderException;
import org.commcare.data.xml.TransactionParser;
import org.javarosa.xform.util.InvalidStructureException;
import org.javarosa.xform.util.UnfullfilledRequirementsException;
import org.joda.time.format.ISODateTimeFormat;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * @author ctsims
 *
 */
public abstract class KeyRecordParser extends TransactionParser<ArrayList<UserKeyRecord>> {
    
    String username;
    String currentpwd;
    ArrayList<UserKeyRecord> keyRecords;

    public KeyRecordParser(KXmlParser parser, String username, String currentpwd, ArrayList<UserKeyRecord> keyRecords) {
        super(parser,"auth_keys", null);
        this.username = username;
        this.currentpwd = currentpwd;
        this.keyRecords = new ArrayList<UserKeyRecord>();
    }

    /*
     * (non-Javadoc)
     * @see org.javarosa.xform.parse.ElementParser#parse()
     */
    @Override
    public ArrayList<UserKeyRecord> parse() throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
        this.checkNode("auth_keys");
            
            //TODO: Domain checking and such
            
        while(this.nextTagInBlock("auth_keys")) {
            this.checkNode("key_record");
            
            Date valid = getDateAttribute("valid", false);
            
            Date expires = getDateAttribute("expires", true);
            
            this.nextTag("uuid");
            
            String title = parser.getAttributeValue(null, "title");
            String uuid = parser.nextText();
            if(uuid == null) { throw new InvalidStructureException("No <uuid> value found for incoming key record", parser); } 
            
            this.nextTag("key");
            
            //We don't really use this for now
            String type = parser.getAttributeValue(null, "type");
    
            //Base64 Encoded AES key
            String encodedKey = parser.nextText();
            byte[] theKey;
            try {
                theKey = Base64.decode(encodedKey);
            } catch (Base64DecoderException e) {
                //Invalid key!
                e.printStackTrace();
                throw new InvalidStructureException("Invalid AES key in key record", parser);
            }
            
            byte[] wrappedKey = CryptUtil.wrapKey(theKey, currentpwd);
            UserKeyRecord record = new UserKeyRecord(username, UserKeyRecord.generatePwdHash(currentpwd), wrappedKey, valid, expires, uuid, UserKeyRecord.TYPE_NEW);
            
            keyRecords.add(record);
        }

        commit(keyRecords);
        return keyRecords;
    }
    
    
    protected Date parseDateTime(String dateValue) {
        return ISODateTimeFormat.dateTimeNoMillis().parseDateTime(dateValue).toDate();
    }

}
