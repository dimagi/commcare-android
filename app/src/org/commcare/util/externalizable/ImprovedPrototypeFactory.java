/**
 * 
 */
package org.commcare.util.externalizable;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;

import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapTagged;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 *
 */
public class ImprovedPrototypeFactory extends PrototypeFactory {
    
    PrefixTree classNames;
    
    Hashtable<Integer, Class> prototypes = new Hashtable<Integer, Class>();
    MessageDigest digest;
    
    public ImprovedPrototypeFactory (PrefixTree classNames) {
        super(classNames);
        this.classNames = classNames;
        try {
            digest = java.security.MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }        

    @Override
    public void addClass (Class c) {
        if (!initialized) {
            lazyInit();
        }
        
        //this is used as a bulk operation, so we custom implement it, the android hash is way faster
        //than the j2me compatible one.
        byte[] hash = getClassHashInternal(c);
        
        if (compareHash(hash, ExtWrapTagged.WRAPPER_TAG)) {
            throw new Error("Hash collision! " + c.getName() + " and reserved wrapper tag");
        }
        
        Class d = getClass(hash);
        if (d != null && d != c) {
            int one = getHash(hash);
            int two = getHash(getClassHash(d));
            throw new Error("Hash collision! " + one + c.getName() + ExtUtil.printBytes(hash) +" and " + d.getName() + ExtUtil.printBytes(getClassHash(d)));
        }
        
        prototypes.put(getHash(hash), c);
    }
    
    public byte[] getClassHashInternal (Class type) {
        byte[] hash = new byte[CLASS_HASH_SIZE];
        
        byte[] md5 = digest.digest(type.getName().getBytes());
        
        for (int i = 0; i < hash.length; i++)
            hash[i] = md5[i];
        byte[] badHash = new byte[] {0,4,78,97};
        if(PrototypeFactory.compareHash(badHash, hash)) {
            System.out.println("BAD CLASS: " + type.getName());
        }
        
        return hash;
    }
    
    private Integer getHash(byte[] hash) {
        return Integer.valueOf((hash[3] << 0) + (hash[2] << 8) + (hash[1] << 16) + (hash[0] << 24));
    }
    
    @Override
    public Class getClass (byte[] hash) {
        if (!initialized) {
            lazyInit();
        }
        return prototypes.get(getHash(hash));
    }

}
