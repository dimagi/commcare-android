/**
 * 
 */
package org.commcare.android.util;

import org.commcare.util.externalizable.AndroidClassHasher;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.util.Hashtable;

/**
 *
 * Prototype factory for testing. Persistable hashes will not be accessible between runtimes.
 *
 * @author ctsims
 *
 */
public class LivePrototypeFactory extends PrototypeFactory {
    
    Hashtable<String, Class> factoryTable = new Hashtable<String, Class>();
    AndroidClassHasher hasher;
    
    public LivePrototypeFactory() {
        hasher = new AndroidClassHasher();
    }

    @Override
    protected void lazyInit() {
    }

    @Override
    public void addClass(Class c) {
        byte[] hash = hasher.getClassHashValue(c);
        factoryTable.put(ExtUtil.printBytes(hash), c);
    }

    @Override
    public Class getClass(byte[] hash) {
        String key = ExtUtil.printBytes(hash);
        return factoryTable.get(key);
    }

    @Override
    public Object getInstance(byte[] hash) {
        return PrototypeFactory.getInstance(getClass(hash));
    }
}
