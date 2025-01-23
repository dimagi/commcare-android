package org.commcare.utils;

import java.security.Key;

/**
 * Utility class for holding an encryption key and transformation string pair
 *
 * @author dviggiano
 */
public class EncryptionKeyAndTransform {
    private final Key key;
    private final String transformation;

    public EncryptionKeyAndTransform(Key key, String transformation) {
        if (key == null) {
            throw new IllegalArgumentException("Encryption key cannot be null");
        }
        if (transformation == null || transformation.trim().isEmpty()) {
            throw new IllegalArgumentException("Transformation string cannot be null or empty");
        }
        if (!transformation.matches("[A-Za-z0-9]+/[A-Za-z0-9]+/[A-Za-z0-9]+Padding"))  {
            throw new IllegalArgumentException("Invalid transformation format. Expected: Algorithm/Mode/Padding");
        }
        // Create defensive copy if key is not immutable
        if (key instanceof javax.crypto.SecretKey) {
            byte[] encodedKey = key.getEncoded();
            if (encodedKey == null) {
                throw new IllegalArgumentException("Key encoding is null or unsupported for this key type");
            }
            this.key = new javax.crypto.spec.SecretKeySpec(encodedKey, key.getAlgorithm());
        } else {
            this.key = key;
        }
        this.transformation = transformation;
    }

    /**
     * @return The encryption key
     */
    public Key getKey() {
        return key;
    }

    /**
     * @return The transformation string
     */
    public String getTransformation() {
        return transformation;
    }
}
