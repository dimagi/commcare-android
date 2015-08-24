package org.commcare.xml;

import org.commcare.core.parse.UserXmlParser;
import org.javarosa.core.model.User;
import org.javarosa.core.services.storage.IStorageUtilityIndexed;
import org.kxml2.io.KXmlParser;



public class AndroidUserXmlParser extends UserXmlParser {

    byte[] wrappedKey;
    
    public AndroidUserXmlParser(KXmlParser parser, IStorageUtilityIndexed<User> storage, byte[] wrappedKey) {
        super(parser, storage);
        this.wrappedKey = wrappedKey;
    }

    @Override
    public void addCustomData(User u){
        if(wrappedKey != null){
            u.setWrappedKey(wrappedKey);
        }
    }
}