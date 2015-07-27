package org.commcare.util.externalizable;

import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.util.Hashtable;

/**
 * @author ctsims
 *
 */
public class ImprovedPrototypeFactory extends PrototypeFactory {
    
    Hashtable<Integer, Class> prototypes;
    
    public ImprovedPrototypeFactory (PrefixTree classNames) {
        super(classNames);
        setStaticHasher(new AndroidClassHasher());
    }

    protected void lazyInit() {
        initialized = true;
        prototypes = new Hashtable<Integer, Class>();
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

    public void storeHash(Class c, byte[] hash){
        prototypes.put(getHash(hash), c);
    }

}
