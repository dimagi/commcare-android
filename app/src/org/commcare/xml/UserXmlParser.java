package org.commcare.xml;

import java.io.IOException;
import java.util.Date;
import java.util.NoSuchElementException;

import org.commcare.android.database.user.models.User;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.data.xml.TransactionParser;
import org.javarosa.xml.util.InvalidStructureException;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.javarosa.core.services.storage.StorageFullException;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;

public class UserXmlParser extends TransactionParser<User> {

    IStorageUtilityIndexed storage;
    Context context;
    byte[] wrappedKey;
    
    public UserXmlParser(KXmlParser parser, Context context, byte[] wrappedKey) {
        super(parser, "registration", null);
        this.context = context;
        this.wrappedKey = wrappedKey;
    }

    public User parse() throws InvalidStructureException, IOException, XmlPullParserException, SessionUnavailableException {
        this.checkNode("registration");
        
        //parse (with verification) the next tag
        this.nextTag("username");
        String username = parser.nextText();
        
        this.nextTag("password");
        String passwordHash = parser.nextText();
        
        this.nextTag("uuid");
        String uuid = parser.nextText();
        
        this.nextTag("date");
        String dateModified = parser.nextText();
        Date modified = DateUtils.parseDateTime(dateModified);
        
        User u = retrieve(uuid);
        
        if(u == null) {
            u = new User(username, passwordHash, uuid);
            u.setWrappedKey(wrappedKey);
        } else {
            if(passwordHash != null && !passwordHash.equals(u.getPassword())) {
                u.setPassword(passwordHash);
                u.setWrappedKey(wrappedKey);
            } 
        }
        
        //Now look for optional components
        while(this.nextTagInBlock("registration")) {
            
            String tag = parser.getName().toLowerCase();
            
            if(tag.equals("registering_phone_id")) {
                String phoneid = parser.nextText();
            } else if(tag.equals("token")) {
                String token = parser.nextText();
            } else if(tag.equals("user_data")) {
                while(this.nextTagInBlock("user_data")) {
                    this.checkNode("data");
                    
                    String key = this.parser.getAttributeValue(null, "key");
                    String value = this.parser.nextText();
                    
                    u.setProperty(key, value);
                }
                
                //This should be the last block in the registration stuff...
                break;
            } else {
                throw new InvalidStructureException("Unrecognized tag in user registraiton data: " + tag,parser);
            }
        }
        
        commit(u);
        return u;
    }

    public void commit(User parsed) throws IOException, SessionUnavailableException {
        try {
            storage().write(parsed);
        } catch (StorageFullException e) {
            e.printStackTrace();
            throw new IOException("Storage full while writing case!");
        }
    }

    public User retrieve(String entityId) throws SessionUnavailableException {
        IStorageUtilityIndexed storage = storage();
        try{
            return (User)storage.getRecordForValue(User.META_UID, entityId);
        } catch(NoSuchElementException nsee) {
            return null;
        }
    }
    
    public IStorageUtilityIndexed storage() throws SessionUnavailableException{
        if(storage == null) {
            storage =  CommCareApplication._().getUserStorage(User.class);
        } 
        return storage;
    }

}
