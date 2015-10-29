package org.commcare.util.externalizable;

import org.javarosa.core.util.PrefixTree;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.util.Hashtable;

/**
 * This class overrides the core PrototypeFactory class primarily because we
 * can store our Android hashes as an int and thus store them in a map for faster lookups. Most
 * other functionality is the same, except we override how we store and retrieve hashes
 * so that we can use the Map.
 *
 * @author ctsims
 * @author wspride
 */
public class AndroidPrototypeFactory extends PrototypeFactory {
    
    private Hashtable<Integer, Class> prototypes;
    
    public AndroidPrototypeFactory(PrefixTree classNames) {
        super(new AndroidClassHasher(), classNames);
    }

    @Override
    protected void lazyInit() {
        initialized = false;
        prototypes = new Hashtable<Integer, Class>();
        super.lazyInit();
    }


    private Integer getHash(byte[] hash) {
        return (hash[3]) + (hash[2] << 8) + (hash[1] << 16) + (hash[0] << 24);
    }
    
    @Override
    public Class getClass (byte[] hash) {
        if (!initialized) {
            lazyInit();
        }
        return prototypes.get(getHash(hash));
    }

    @Override
    public void storeHash(Class c, byte[] hash){
        prototypes.put(getHash(hash), c);
    }

}
