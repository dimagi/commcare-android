package org.commcare.utils;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKey;

public class MockKeyStore extends KeyStoreSpi {

    private static HashMap<String, Key> keys = new HashMap<>();
    private static HashMap<String, Certificate> certs = new HashMap<>();

    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) {
        keys.put(alias, key);
    }

    @Override
    public void engineDeleteEntry(String alias) throws KeyStoreException {
        keys.remove(alias);
        certs.remove(alias);
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        return keys.containsKey(alias) || certs.containsKey(alias);
    }

    @Override
    public int engineSize() {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(keys.keySet());
        allKeys.addAll(certs.keySet());
        return allKeys.size();
    }

    @Override
    public boolean engineIsKeyEntry(String alias) {
        return keys.containsKey(alias);
    }

    @Override
    public boolean engineIsCertificateEntry(String alias) {
        return certs.containsKey(alias);
    }

    @Override
    public KeyStore.Entry engineGetEntry(String alias, KeyStore.ProtectionParameter protParam) {
        Key key = keys.get(alias);
        if (key != null) {
            if (key instanceof SecretKey){
                return new KeyStore.SecretKeyEntry((SecretKey)key);
            } else if (key instanceof PrivateKey) {
                return new KeyStore.PrivateKeyEntry((PrivateKey)key, null);
            }
        }
        Certificate cert = certs.get(alias);
        if (cert != null) {
            return new KeyStore.TrustedCertificateEntry(cert);
        }
        throw new UnsupportedOperationException(String.format("No alias found in keys or certs, alias=%s", alias));
    }

    @Override
    public Key engineGetKey(String alias, char[] password) {
        return null;
    }

    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        return null;
    }

    @Override
    public Certificate engineGetCertificate(String alias) {
        return null;
    }

    @Override
    public Date engineGetCreationDate(String alias) {
        return null;
    }

    @Override
    public String engineGetCertificateAlias(Certificate cert) {
        return null;
    }

    @Override
    public void engineStore(OutputStream stream, char[] password) {
        // Do nothing, this is a mock key store
    }

    @Override
    public void engineLoad(InputStream stream, char[] password) {
        // Do nothing, this is a mock key store
    }

    @Override
    public Enumeration<String> engineAliases() {
        return null;
    }

    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) {
        // Do nothing, this is a mock key store for secret keys
    }

    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert)  {
        // Do nothing, this is a mock key store for secret keys
    }
}


