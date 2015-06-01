package org.commcare.util.externalizable;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.javarosa.core.util.externalizable.Hasher;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 */
public class AndroidClassHasher implements Hasher {
    private static final String TAG = "AndroidClassHasher";

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

    @Override
    public byte[] getClassHashValue(Class type) {
        byte[] hash = new byte[PrototypeFactory.CLASS_HASH_SIZE];
        
        byte[] md5;
        synchronized(mMessageDigester) {
             md5 = mMessageDigester.digest(type.getName().getBytes());
        }
        
        for (int i = 0; i < hash.length; i++) {
            hash[i] = md5[i];
        }
        byte[] badHash = new byte[] {0,4,78,97};
        if(PrototypeFactory.compareHash(badHash, hash)) {
            Log.d(TAG, "BAD CLASS: " + type.getName());
        }
        
        return hash;
    }

}
