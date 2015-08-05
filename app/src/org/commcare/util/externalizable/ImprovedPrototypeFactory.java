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
        super(new AndroidClassHasher(), classNames);
    }

    protected void lazyInit() {
        initialized = false;
        prototypes = new Hashtable<Integer, Class>();
        super.lazyInit();
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

    public void storeHash(Class c, byte[] hash){
        prototypes.put(getHash(hash), c);
    }

}
