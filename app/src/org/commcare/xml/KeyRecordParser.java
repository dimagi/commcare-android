package org.commcare.xml;

import org.commcare.data.xml.TransactionParser;
import org.commcare.models.database.app.models.UserKeyRecord;
import org.commcare.models.encryption.ByteEncrypter;
import org.commcare.utils.Base64;
import org.commcare.utils.Base64DecoderException;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.xml.util.UnfullfilledRequirementsException;
import org.joda.time.format.ISODateTimeFormat;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

/**
 * @author ctsims
 */
public abstract class KeyRecordParser extends TransactionParser<ArrayList<UserKeyRecord>> {

    private final String username;
    private final String currentpwd;
    private final ArrayList<UserKeyRecord> keyRecords;

    public KeyRecordParser(KXmlParser parser, String username, String currentpwd) {
        super(parser);
        this.username = username;
        this.currentpwd = currentpwd;
        this.keyRecords = new ArrayList<>();
    }

    @Override
    public ArrayList<UserKeyRecord> parse() throws InvalidStructureException, IOException, XmlPullParserException, UnfullfilledRequirementsException {
        this.checkNode("auth_keys");

        //TODO: Domain checking and such

        while (this.nextTagInBlock("auth_keys")) {
            this.checkNode("key_record");

            Date valid = getDateAttribute("valid", false);

            Date expires = getDateAttribute("expires", true);

            this.nextTag("uuid");

            parser.getAttributeValue(null, "title");
            String uuid = parser.nextText();
            if (uuid == null) {
                throw new InvalidStructureException("No <uuid> value found for incoming key record", parser);
            }

            this.nextTag("key");

            //We don't really use this for now
            parser.getAttributeValue(null, "type");

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

            byte[] wrappedKey = ByteEncrypter.wrapByteArrayWithString(theKey, currentpwd);
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
