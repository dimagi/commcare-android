package org.commcare.util.externalizable;

import org.javarosa.core.util.externalizable.Hasher;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author ctsims
 */
public class AndroidClassHasher extends Hasher {

    private static final int CLASS_HASH_SIZE = 4;

    MessageDigest mMessageDigester;
    
    public AndroidClassHasher() {
        try {
            mMessageDigester = java.security.MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public static void registerAndroidClassHashStrategy() {
        PrototypeFactory.setStaticHasher(new AndroidClassHasher());
    }

    public byte[] getHash(Class c){
        byte[] ret;
        synchronized(mMessageDigester) {
            ret = mMessageDigester.digest(c.getName().getBytes());
        }
        return ret;
    }


    @Override
    public int getHashSize(){
        return CLASS_HASH_SIZE;
    }


}
