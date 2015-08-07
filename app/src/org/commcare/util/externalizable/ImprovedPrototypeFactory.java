package org.commcare.util.externalizable;

import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.ExtWrapTagged;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.util.Hashtable;

/**
 * @author ctsims
 *
 */
public class ImprovedPrototypeFactory extends PrototypeFactory {
    
    PrefixTree classNames;
    
    Hashtable<Integer, Class> prototypes = new Hashtable<Integer, Class>();
    AndroidClassHasher hasher;
    
    public ImprovedPrototypeFactory (PrefixTree classNames) {
        super(classNames);
        this.classNames = classNames;
        hasher = new AndroidClassHasher();
    }

    @Override
    public void addClass (Class c) {
        if (!initialized) {
            lazyInit();
        }
        
        //this is used as a bulk operation, so we custom implement it, the android hash is way faster
        //than the j2me compatible one.
        byte[] hash = hasher.getClassHashValue(c);
        
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
    
    private Integer getHash(byte[] hash) {
        return (hash[3] << 0) + (hash[2] << 8) + (hash[1] << 16) + (hash[0] << 24);
    }
    
    @Override
    public Class getClass (byte[] hash) {
        if (!initialized) {
            lazyInit();
        }
        return prototypes.get(getHash(hash));
    }

}
