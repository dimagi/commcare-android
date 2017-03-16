package org.commcare.models;

import org.javarosa.core.util.externalizable.Hasher;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

/**
 * @author ctsims
 */
public class AndroidClassHasher extends Hasher {

    private static AndroidClassHasher instance;
    private static final int CLASS_HASH_SIZE = 4;

    private final MessageDigest mMessageDigester;

    private final HashMap<String, byte[]> classNameHashMap = new HashMap<>();

    private AndroidClassHasher() {
        try {
            mMessageDigester = java.security.MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized static AndroidClassHasher getInstance() {
        if (instance == null) {
            instance = new AndroidClassHasher();
        }

        return instance;
    }

    public static void registerAndroidClassHashStrategy() {
        PrototypeFactory.setStaticHasher(getInstance());
    }

    @Override
    public synchronized byte[] getHash(Class c) {
        String name = c.getName();
        if(classNameHashMap.containsKey(name)) {
            return classNameHashMap.get(name);
        }
        byte[] hash = mMessageDigester.digest(name.getBytes());
        classNameHashMap.put(name, hash);
        return hash;
    }

    public synchronized byte[] getClassnameHash(String className) {
        return Arrays.copyOf(mMessageDigester.digest(className.getBytes()), CLASS_HASH_SIZE);
    }

    @Override
    public int getHashSize() {
        return CLASS_HASH_SIZE;
    }
}
