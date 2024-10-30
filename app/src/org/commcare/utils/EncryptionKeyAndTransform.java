package org.commcare.utils;

import java.security.Key;

/**
 * Utility class for holding an encryption key and transformation string pair
 *
 * @author dviggiano
 */
public class EncryptionKeyAndTransform {
    public Key key;
    public String transformation;

    public EncryptionKeyAndTransform(Key key, String transformation) {
        this.key = key;
        this.transformation = transformation;
    }
}
