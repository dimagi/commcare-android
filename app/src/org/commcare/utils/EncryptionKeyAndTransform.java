package org.commcare.utils;

import java.security.Key;

public class EncryptionKeyAndTransform {
    public Key key;
    public String transformation;
    public EncryptionKeyAndTransform(Key key, String transformation) {
        this.key = key;
        this.transformation = transformation;
    }
}
