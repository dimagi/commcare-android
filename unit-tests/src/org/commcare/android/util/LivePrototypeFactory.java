/**
 * 
 */
package org.commcare.android.util;

import java.util.Hashtable;

import org.commcare.util.externalizable.AndroidClassHasher;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.Hasher;
import org.javarosa.core.util.externalizable.PrototypeFactory;

/**
 * @author ctsims
 *
 */
public class LivePrototypeFactory extends PrototypeFactory implements Hasher {
    
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

    @Override
    public byte[] getClassHashValue(Class type) {
        byte[] hash = hasher.getClassHashValue(type);
        factoryTable.put(ExtUtil.printBytes(hash), type);
        return hash;
    }

}
