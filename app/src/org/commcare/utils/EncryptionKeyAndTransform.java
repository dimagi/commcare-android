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
